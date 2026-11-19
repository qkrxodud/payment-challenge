package com.example.payment.infrastructure.kafka.dto;

import com.example.payment.domain.payment.PaymentEvent;
import java.time.Instant;

/** 9장: 브로커에 싣는 결제 이벤트의 본문(JSON) */
public record PaymentEventMessage(String eventId, Long paymentId, String orderId, String status, Instant occurredAt) {

    public static PaymentEventMessage from(PaymentEvent event) {
        return new PaymentEventMessage(event.getEventId(), event.getPaymentId(), event.getOrderId(), event.getStatus().name(),
                event.getOccurredAt());
    }
}
