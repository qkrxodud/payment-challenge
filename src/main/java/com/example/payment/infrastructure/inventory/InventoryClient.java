package com.example.payment.infrastructure.inventory;

import com.example.payment.domain.error.exception.InventoryFailedException;
import com.example.payment.domain.error.exception.InventoryNoResponseException;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.stock.StockReservations;
import com.example.payment.global.config.InventoryProperties;
import com.example.payment.infrastructure.inventory.dto.StockChangeRequest;
import java.net.http.HttpClient;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 10장: 재고 서비스 HTTP 호출. 결제 하나에 담긴 상품을 한 요청으로 보낸다.
 * 잡기: 200 은 잡음, 409 는 재고 없음, 그 밖의 답은 재고 서비스 오류. 답을 받지 못하면(시간 초과·연결 실패) 응답 없음으로 알린다.
 * 돌려주기: 실패해도 결제 기록을 막지 않게 로그만 남긴다(출발 구조 그대로).
 */
@Slf4j
@Component
@EnableConfigurationProperties(InventoryProperties.class)
public class InventoryClient implements StockReservations {

    private final RestClient inventoryRestClient;

    public InventoryClient(InventoryProperties inventoryProperties) {
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
    public boolean reserve(String orderId, List<PaymentItem> items) {
        try {
            return inventoryRestClient.post()
                    .uri("/stocks/decrease")
                    .body(StockChangeRequest.of(items))
                    .exchange((request, response) -> isReserved(orderId, response.getStatusCode().value()));
        } catch (ResourceAccessException noResponse) {
            throw new InventoryNoResponseException(orderId);
        }
    }

    private static boolean isReserved(String orderId, int status) {
        if (HttpStatus.valueOf(status).is2xxSuccessful()) {
            return true;
        }
        if (status == HttpStatus.CONFLICT.value()) {
            return false;
        }
        throw new InventoryFailedException(orderId, status);
    }

    @Override
    public void release(String orderId, List<PaymentItem> items) {
        try {
            inventoryRestClient.post()
                    .uri("/stocks/increase")
                    .body(StockChangeRequest.of(items))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException notReleased) {
            log.warn("Could not return stock of order {}: {}.", orderId, notReleased.getClass().getSimpleName());
        }
    }
}
