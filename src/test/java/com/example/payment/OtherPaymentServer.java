package com.example.payment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 7장: 시험 JVM 안에 결제 서버를 한 대 더 띄운다(서버 여러 대). 시험 서버와 같은 MySQL, 같은 Mock PG 를 쓴다.
 * 주기 작업(보정, 받은 알림 처리)은 끄고 시험이 직접 부른다. 웹 서버는 띄우지 않는다 — 주기 작업만 본다.
 * 시험 설정(@TestConfiguration: 시험용 MySQL·Mock PG 를 따로 띄우는 설정)은 읽지 않는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OtherPaymentServer {

    public static ConfigurableApplicationContext start(MySQLContainer mysql, MockPgServer mockPg, List<String> properties,
                                                       Class<?>... components) {
        List<String> all = new ArrayList<>(List.of(
                "spring.main.web-application-type=none",
                "spring.datasource.url=" + mysql.getJdbcUrl(),
                "spring.datasource.username=" + mysql.getUsername(),
                "spring.datasource.password=" + mysql.getPassword(),
                "payment.pg.base-url=" + mockPg.baseUrl(),
                "payment.reconciliation.enabled=false",
                "payment.webhook.enabled=false"));
        all.addAll(properties);
        // 기본 속성(properties)은 application.properties 보다 약하다. 실행 인자로 넘겨 설정 파일 값(PG 주소 등)보다 앞세운다
        return new SpringApplicationBuilder(PaymentChallengeApplication.class)
                .sources(components)
                .initializers(context -> context.getBeanFactory()
                        .registerSingleton("skipTestConfiguration", new SkipTestConfiguration()))
                .run(all.stream().map(property -> "--" + property).toArray(String[]::new));
    }

    // 시험 서버만 쓰는 설정을 다른 서버가 읽지 않게 한다(Spring Boot 시험이 하는 것과 같은 방법)
    static class SkipTestConfiguration extends TypeExcludeFilter {

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
            return metadataReader.getAnnotationMetadata().hasAnnotation(TestConfiguration.class.getName());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SkipTestConfiguration;
        }

        @Override
        public int hashCode() {
            return SkipTestConfiguration.class.hashCode();
        }
    }
}
