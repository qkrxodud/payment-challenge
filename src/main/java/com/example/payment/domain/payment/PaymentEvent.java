package com.example.payment.domain.payment;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 9장: 주문 서비스에 알리는 결제 결과 한 건. 결제가 결과 상태(승인, 승인 실패, 만료, 매입, 승인 취소, 매입 취소)로 바뀔 때 만든다.
 * eventId 는 이벤트마다 붙는 고유 번호다 — 받는 쪽이 같은 이벤트가 다시 왔는지 셀 수 있게 한다.
 */
@Getter
public class PaymentEvent {

    private final String eventId;
    private final Long paymentId;
    private final String orderId;
    private final PaymentStatus status;
    private final Instant occurredAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentEvent(String eventId, Long paymentId, String orderId, PaymentStatus status, Instant occurredAt) {
        this.eventId = eventId;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = status;
        this.occurredAt = occurredAt;
    }

    public static PaymentEvent of(String eventId, Long paymentId, String orderId, PaymentStatus status, Instant occurredAt) {
        return PaymentEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .orderId(orderId)
                .status(status)
                .occurredAt(occurredAt)
                .build();
    }

    public static PaymentEvent of(String eventId, Payment payment, Instant occurredAt) {
        return of(eventId, payment.getId(), payment.getOrderId(), payment.getStatus(), occurredAt);
    }
}
