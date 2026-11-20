package com.example.payment.infrastructure.inventory;

import com.example.payment.global.config.InventoryProperties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 10장: 재고 서비스 HTTP 클라이언트. 결정 1: 재고 서비스의 답을 기다리는 시간이 결제 응답에 더해지므로 시간 제한을 짧게 둔다 */
@Configuration
@EnableConfigurationProperties(InventoryProperties.class)
public class InventoryClientConfig {

    @Bean
    RestClient inventoryRestClient(InventoryProperties inventoryProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(inventoryProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(inventoryProperties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(inventoryProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
