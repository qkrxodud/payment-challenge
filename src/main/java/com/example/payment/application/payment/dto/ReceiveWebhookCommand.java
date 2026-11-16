package com.example.payment.application.payment.dto;

// 6장: 카드사가 보낸 웹훅 한 건. reportedStatus 는 본문 data.status, reportedCreatedAt 은 본문 createdAt 그대로다
public record ReceiveWebhookCommand(String eventType, String orderId, String reportedStatus, String reportedCreatedAt) {

    private static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";

    // 결제 상태가 바뀌었다는 알림인가(토스페이먼츠 PAYMENT_STATUS_CHANGED)
    public boolean isPaymentStatusChange() {
        return PAYMENT_STATUS_CHANGED.equals(eventType);
    }
}
