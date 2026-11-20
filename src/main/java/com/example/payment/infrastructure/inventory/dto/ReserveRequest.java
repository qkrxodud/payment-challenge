package com.example.payment.infrastructure.inventory.dto;

import com.example.payment.domain.payment.PaymentItem;
import java.util.List;

/** 10장 결정 3: 재고 서비스에 보내는 예약 요청. 예약 번호는 결제 번호다 */
public record ReserveRequest(Long reservationId, List<Line> items) {

    public static ReserveRequest of(Long reservationId, List<PaymentItem> items) {
        return new ReserveRequest(reservationId, items.stream()
                .map(item -> new Line(item.getProductId(), item.getQuantity()))
                .toList());
    }

    /** 상품 하나와 그 수량 */
    public record Line(Long productId, int quantity) {
    }
}
