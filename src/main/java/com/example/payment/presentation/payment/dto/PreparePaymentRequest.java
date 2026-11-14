package com.example.payment.presentation.payment.dto;

import java.math.BigDecimal;

public record PreparePaymentRequest(String orderId, BigDecimal amount) {
}
