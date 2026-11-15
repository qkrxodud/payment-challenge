package com.example.payment.infrastructure.pg.dto;

// 카드사 결제 조회 응답에서 읽는 필드만 둔다
public record PgPaymentResponse(String orderId, String status) {
}
