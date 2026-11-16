package com.example.payment.domain.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 받은 카드사 웹훅 한 건(6장 결정 4). 받자마자 남기고 200 으로 답한다. 결제(Payment)와 다른 묶음이라 주문번호로만 가리킨다 —
 * 모르는 주문의 알림도 남긴다. 본문의 상태·시각은 받은 그대로 기록으로만 둔다(결제를 맞출 때는 카드사 조회를 믿는다).
 */
@Entity
@Table(name = "payment_webhook")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false, length = 64)
    private String orderId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @Column(name = "reported_status", updatable = false, length = 30)
    private String reportedStatus;

    @Column(name = "reported_created_at", updatable = false, length = 40)
    private String reportedCreatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookStatus status;

    @Column(name = "failed_lookups", nullable = false)
    private int failedLookups;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentWebhook(String orderId, String eventType, String reportedStatus, String reportedCreatedAt,
                           WebhookStatus status, Instant receivedAt) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.reportedStatus = reportedStatus;
        this.reportedCreatedAt = reportedCreatedAt;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public static PaymentWebhook received(String orderId, String eventType, String reportedStatus, String reportedCreatedAt,
                                          Instant now) {
        return PaymentWebhook.builder()
                .orderId(orderId)
                .eventType(eventType)
                .reportedStatus(reportedStatus)
                .reportedCreatedAt(reportedCreatedAt)
                .status(WebhookStatus.RECEIVED)
                .receivedAt(now)
                .build();
    }

    // 카드사 조회로 결제를 맞췄다. 이미 처리한 알림은 다시 바꾸지 않는다
    public void process(Instant now) {
        close(WebhookStatus.PROCESSED, now);
    }

    // 우리가 모르는 주문이라 볼 결제가 없다
    public void ignore(Instant now) {
        close(WebhookStatus.IGNORED, now);
    }

    // 카드사 조회에 실패했다. 받은 상태로 남겨 다음 처리 때 다시 본다
    public void failLookup() {
        failedLookups++;
    }

    private void close(WebhookStatus closed, Instant now) {
        if (status.isReceived()) {
            this.status = closed;
            this.processedAt = now;
        }
    }
}
