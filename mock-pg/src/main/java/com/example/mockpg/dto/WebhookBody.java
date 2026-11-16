package com.example.mockpg.dto;

/**
 * 6장: 웹훅 본문. 토스페이먼츠 PAYMENT_STATUS_CHANGED 와 같은 모양: eventType, createdAt(yyyy-MM-dd'T'HH:mm:ss.SSSSSS),
 * data(상태가 바뀐 Payment 객체). 문서대로 본문에 eventId 는 없다
 */
public record WebhookBody(String eventType, String createdAt, PaymentResponse data) {
}
