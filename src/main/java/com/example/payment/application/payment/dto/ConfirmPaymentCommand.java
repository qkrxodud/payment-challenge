package com.example.payment.application.payment.dto;

import java.math.BigDecimal;

public record ConfirmPaymentCommand(String orderId, String paymentKey, BigDecimal amount) {
}
