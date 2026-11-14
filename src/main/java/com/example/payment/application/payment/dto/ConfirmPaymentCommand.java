package com.example.payment.application.payment.dto;

public record ConfirmPaymentCommand(String orderId, String paymentKey) {
}
