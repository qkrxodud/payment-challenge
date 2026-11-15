package com.example.payment.presentation.payment.dto;

import java.math.BigDecimal;

public record CancelPaymentRequest(BigDecimal cancelAmount, String cancelReason) {
}
