package com.example.payment.infrastructure.inventory;

import com.example.payment.domain.error.exception.InventoryFailedException;
import com.example.payment.domain.error.exception.InventoryNoResponseException;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.stock.StockReservations;
import com.example.payment.global.config.InventoryProperties;
import com.example.payment.infrastructure.inventory.dto.ReserveRequest;
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
 * 10장: 재고 서비스 HTTP 호출. 예약 번호는 결제 번호다(결정 3).
 * 예약: 200 은 잡음, 409 는 잡지 않음(재고 없음, 이미 해제된 예약), 그 밖의 답은 재고 서비스 오류. 답을 받지 못하면(시간 초과·연결 실패) 응답 없음.
 * 확정·해제: 상태를 바꾼 그 자리에서 보내고, 실패해도 결제 기록을 막지 않게 로그만 남긴다.
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
    public boolean reserve(Long reservationId, String orderId, List<PaymentItem> items) {
        try {
            return inventoryRestClient.post()
                    .uri("/reservations")
                    .body(ReserveRequest.of(reservationId, items))
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
    public void confirm(Long reservationId, String orderId) {
        send("/reservations/{reservationId}/confirm", reservationId, orderId);
    }

    @Override
    public void release(Long reservationId, String orderId, List<PaymentItem> items) {
        send("/reservations/{reservationId}/release", reservationId, orderId);
    }

    private void send(String uri, Long reservationId, String orderId) {
        try {
            inventoryRestClient.post()
                    .uri(uri, reservationId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException notSent) {
            log.warn("Could not send {} for order {}: {}.", uri, orderId, notSent.getClass().getSimpleName());
        }
    }
}
