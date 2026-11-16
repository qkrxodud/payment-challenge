package com.example.payment.presentation.webhook;

import com.example.payment.presentation.webhook.comparison.WebhookComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("카드사 웹훅 처리 — 비교 A: 본문의 상태를 그대로 믿음")
class BodyStatusWebhookTest extends WebhookContract {

    @Override
    String webhookPath() {
        return WebhookComparisonConfiguration.BODY_STATUS;
    }

    // 받은 자리에서 옮긴다
    @Override
    void process() {
    }
}
