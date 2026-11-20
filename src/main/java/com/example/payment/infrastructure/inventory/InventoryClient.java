package com.example.payment.infrastructure.inventory;

import com.example.payment.domain.error.exception.InventoryFailedException;
import com.example.payment.domain.error.exception.InventoryNoResponseException;
import com.example.payment.domain.payment.PaymentItem;
import com.example.payment.domain.stock.StockReservations;
import com.example.payment.infrastructure.inventory.dto.ReserveRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 10장: 재고 서비스 HTTP 호출. 예약 번호는 결제 번호다(결정 3).
 * 예약: 200 은 잡음, 409 는 잡지 않음(재고 없음, 이미 해제된 예약), 그 밖의 답은 재고 서비스 오류. 답을 받지 못하면(시간 초과·연결 실패) 응답 없음.
 * 확정·해제는 결정 5부터 HTTP 가 아니라 재고 명령(payment_outbox → 릴레이 → Kafka)으로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class InventoryClient implements StockReservations {

    private final RestClient inventoryRestClient;

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
}
