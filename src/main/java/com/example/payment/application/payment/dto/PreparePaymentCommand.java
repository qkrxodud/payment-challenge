package com.example.payment.application.payment.dto;

import java.math.BigDecimal;

public record PreparePaymentCommand(String orderId, BigDecimal amount) {
}
