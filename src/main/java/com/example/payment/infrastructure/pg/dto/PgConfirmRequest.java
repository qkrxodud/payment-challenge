package com.example.payment.infrastructure.pg.dto;

import java.math.BigDecimal;

public record PgConfirmRequest(String paymentKey, String orderId, BigDecimal amount) {
}
