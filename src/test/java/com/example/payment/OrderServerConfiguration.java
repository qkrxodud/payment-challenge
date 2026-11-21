package com.example.payment;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.util.ClassUtils;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 9장: 시험 서버와 같은 MySQL 컨테이너(다른 스키마), 같은 Kafka 브로커를 쓰는 주문 서비스를 띄운다.
 * 11장: 결제 이벤트 토픽을 이 컨텍스트만의 이름으로 만들어 결제 서버(payment.events.topic)와 주문 서비스가 함께 쓴다.
 * 주문 서비스에 비교 구현을 끼우는 시험은 test.order.components(쉼표로 나눈 클래스 이름)를, 주문 서비스 설정은 test.order.properties(계약)와
 * test.order.extra-properties(비교 구현)에 세미콜론으로 나눠 둔다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class OrderServerConfiguration {

    @Bean(destroyMethod = "stop")
    OrderServer orderServer(MySQLContainer mysqlContainer, Environment environment) {
        List<String> properties = Stream.concat(
                        listOf(environment.getProperty("test.order.properties", ""), ";").stream(),
                        listOf(environment.getProperty("test.order.extra-properties", ""), ";").stream())
                .toList();
        return OrderServer.start(mysqlContainer, OrderServer.newTopic(),
                classesOf(environment.getProperty("test.order.components", "")), properties);
    }

    @Bean
    DynamicPropertyRegistrar orderTopic(OrderServer orderServer) {
        return registry -> registry.add("payment.events.topic", orderServer::topic);
    }

    private static List<Class<?>> classesOf(String names) {
        return listOf(names, ",").stream()
                .<Class<?>>map(name -> ClassUtils.resolveClassName(name, OrderServerConfiguration.class.getClassLoader()))
                .toList();
    }

    private static List<String> listOf(String joined, String separator) {
        return Arrays.stream(joined.split(separator))
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .toList();
    }
}
