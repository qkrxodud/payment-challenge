package com.example.payment.presentation.webhook;

import com.example.payment.presentation.webhook.comparison.WebhookComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("카드사 웹훅 처리 — 비교 B: 허용 전이 표에 없는 전이는 오류로 답함")
class RejectOffTableWebhookTest extends WebhookContract {

    @Override
    String webhookPath() {
        return WebhookComparisonConfiguration.REJECT_OFF_TABLE;
    }

    // 받은 자리에서 옮긴다
    @Override
    void process() {
    }
}
