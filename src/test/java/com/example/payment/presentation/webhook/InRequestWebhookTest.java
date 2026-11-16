package com.example.payment.presentation.webhook;

import com.example.payment.presentation.webhook.comparison.WebhookComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("카드사 웹훅 처리 — 비교 E: 받은 요청 안에서 카드사 조회와 반영까지 끝내고 답함")
class InRequestWebhookTest extends WebhookContract {

    @Override
    String webhookPath() {
        return WebhookComparisonConfiguration.IN_REQUEST;
    }

    // 받은 자리에서 옮긴다
    @Override
    void process() {
    }
}
