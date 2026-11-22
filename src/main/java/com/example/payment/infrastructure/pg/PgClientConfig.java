package com.example.payment.infrastructure.pg;

import com.example.payment.global.config.DailyReconciliationProperties;
import com.example.payment.global.config.PgProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({PgProperties.class, DailyReconciliationProperties.class})
public class PgClientConfig {

    @Bean
    RestClient pgRestClient(PgProperties pgProperties) {
        return restClient(pgProperties, pgProperties.getReadTimeout());
    }

    // 12장 결정 1: 거래 조회는 승인·조회보다 오래 걸릴 수 있어(문서 60초) 읽기 시간 제한을 따로 둔다
    @Bean
    RestClient pgTransactionRestClient(PgProperties pgProperties) {
        return restClient(pgProperties, pgProperties.getTransactionsReadTimeout());
    }

    private static RestClient restClient(PgProperties pgProperties, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(pgProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(pgProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
