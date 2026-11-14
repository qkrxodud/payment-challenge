package com.example.payment.application.payment.dto;

import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;

public record PaymentResult(String orderId, BigDecimal amount, PaymentStatus status) {

    public static PaymentResult from(Payment payment) {
        return new PaymentResult(payment.getOrderId(), payment.getAmount(), payment.getStatus());
    }
}
