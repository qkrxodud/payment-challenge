package com.example.order.presentation.event.dto;

import java.time.Instant;

/** 9장: 결제 서버가 보내는 결제 이벤트의 본문(JSON) */
public record PaymentEventMessage(String eventId, Long paymentId, String orderId, String status, Instant occurredAt) {
}
