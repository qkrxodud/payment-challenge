package com.example.payment.presentation.payment.dto;

import java.math.BigDecimal;

public record ConfirmPaymentRequest(String paymentKey, BigDecimal amount) {
}
