package com.example.payment.presentation.payment.dto;

import com.example.payment.application.payment.dto.PaymentResult;
import java.math.BigDecimal;

public record PaymentResponse(String orderId, BigDecimal amount, String status) {

    public static PaymentResponse from(PaymentResult result) {
        return new PaymentResponse(result.orderId(), result.amount(), result.status().name());
    }
}
