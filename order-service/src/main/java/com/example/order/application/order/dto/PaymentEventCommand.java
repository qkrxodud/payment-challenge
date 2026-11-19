package com.example.order.application.order.dto;

import java.time.Instant;

/** 9장: 받은 결제 이벤트 한 건(고유 번호, 주문번호, 결제 결과, 만든 때)과 그 이벤트가 실려 온 자리(파티션·위치) */
public record PaymentEventCommand(String eventId, String orderId, String paymentStatus, Instant occurredAt, int partition,
                                  long offset) {
}
