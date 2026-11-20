package com.example.payment.infrastructure.inventory;

import com.example.payment.domain.error.exception.InventoryFailedException;
import com.example.payment.domain.error.exception.InventoryNoResponseException;
import com.example.payment.domain.stock.StockRepository;
import com.example.payment.global.config.InventoryProperties;
import com.example.payment.infrastructure.inventory.dto.StockChangeRequest;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 10장 출발 구조: 재고 저장소(8장 StockRepository)를 재고 서비스 HTTP 호출로 바꿨다. 결제 서버의 다른 코드는 그대로다 —
 * 8장처럼 인증 트랜잭션 안에서 줄이고, 결제가 승인 없이 끝나거나 전액 취소되면 상태를 바꾼 그 트랜잭션 안에서 늘린다.
 * 줄이기: 200 은 줄임, 409 는 재고 없음. 답을 받지 못하면(시간 초과·연결 실패) 응답 없음으로 알린다 — 인증 트랜잭션이 되돌려진다.
 * 늘리기: 실패해도 결제 기록을 막지 않게 로그만 남긴다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(InventoryProperties.class)
public class InventoryStockClient implements StockRepository {

    private final RestClient inventoryRestClient;

    public InventoryStockClient(InventoryProperties inventoryProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(inventoryProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(inventoryProperties.getReadTimeout());
        this.inventoryRestClient = RestClient.builder()
                .baseUrl(inventoryProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean decrease(Long productId, int quantity) {
        try {
            return inventoryRestClient.post()
                    .uri("/stocks/{productId}/decrease", productId)
                    .body(new StockChangeRequest(quantity))
                    .exchange((request, response) -> isDecreased(productId, response.getStatusCode().value()));
        } catch (ResourceAccessException noResponse) {
            throw new InventoryNoResponseException("product-" + productId);
        }
    }

    private static boolean isDecreased(Long productId, int status) {
        if (HttpStatus.valueOf(status).is2xxSuccessful()) {
            return true;
        }
        if (status == HttpStatus.CONFLICT.value()) {
            return false;
        }
        throw new InventoryFailedException("product-" + productId, status);
    }

    @Override
    public void increase(Long productId, int quantity) {
        try {
            inventoryRestClient.post()
                    .uri("/stocks/{productId}/increase", productId)
                    .body(new StockChangeRequest(quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException notIncreased) {
            log.warn("Could not return stock of product {}: {}.", productId, notIncreased.getClass().getSimpleName());
        }
    }
}
