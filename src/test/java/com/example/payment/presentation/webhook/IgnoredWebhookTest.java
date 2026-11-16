package com.example.payment.presentation.webhook;

import com.example.payment.application.payment.PaymentCancelReconciler;
import com.example.payment.application.payment.PaymentReconciler;
import com.example.payment.presentation.webhook.comparison.WebhookComparisonConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("카드사 웹훅 처리 — 비교 F: 알림을 무시하고 주기 보정만 함")
class IgnoredWebhookTest extends WebhookContract {

    @Autowired
    PaymentReconciler paymentReconciler;

    @Autowired
    PaymentCancelReconciler paymentCancelReconciler;

    @Override
    String webhookPath() {
        return WebhookComparisonConfiguration.IGNORED;
    }

    // 알림 대신 4·5장 주기 보정을 지금 시각으로 한 번 돌린다. 보정 대기 시간(10초)이 지나지 않은 결제·취소는 보지 않는다
    @Override
    void process() {
        paymentReconciler.reconcile(Instant.now());
        paymentCancelReconciler.reconcile(Instant.now());
    }
}
