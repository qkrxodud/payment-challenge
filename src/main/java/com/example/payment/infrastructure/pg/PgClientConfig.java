package com.example.payment.infrastructure.pg;

import com.example.payment.global.config.PgProperties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PgProperties.class)
public class PgClientConfig {

    @Bean
    RestClient pgRestClient(PgProperties pgProperties) {
        return RestClient.builder()
                .baseUrl(pgProperties.getBaseUrl())
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()))
                .build();
    }
}
