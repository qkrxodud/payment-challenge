package com.example.payment.presentation.webhook.dto;

import com.example.payment.application.payment.dto.ReceiveWebhookCommand;
import java.util.Objects;

// 6장: 토스페이먼츠 웹훅 본문에서 읽는 필드만 둔다. data 는 상태가 바뀐 Payment 객체다
public record PaymentWebhookRequest(String eventType, String createdAt, PaymentWebhookData data) {

    public ReceiveWebhookCommand toCommand() {
        if (Objects.isNull(data)) {
            return new ReceiveWebhookCommand(eventType, null, null, createdAt);
        }
        return new ReceiveWebhookCommand(eventType, data.orderId(), data.status(), createdAt);
    }
}
