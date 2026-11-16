package com.example.payment.presentation.webhook.dto;

// 6장: 웹훅 본문 data(Payment 객체)에서 읽는 필드
public record PaymentWebhookData(String orderId, String status) {
}
