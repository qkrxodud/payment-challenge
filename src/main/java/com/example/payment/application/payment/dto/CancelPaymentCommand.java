package com.example.payment.application.payment.dto;

import java.math.BigDecimal;

// cancelAmount 가 없으면 남은 잔액을 모두 취소한다
public record CancelPaymentCommand(String orderId, String idempotencyKey, BigDecimal cancelAmount, String cancelReason) {
}
