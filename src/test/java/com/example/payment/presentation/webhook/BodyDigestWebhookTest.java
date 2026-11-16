package com.example.payment.presentation.webhook;

import com.example.payment.presentation.webhook.comparison.WebhookComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("카드사 웹훅 처리 — 비교 D: 받은 본문을 남겨 같은 본문을 거르고, 알림 한 건을 변화 한 건으로 처리함")
class BodyDigestWebhookTest extends WebhookContract {

    @Override
    String webhookPath() {
        return WebhookComparisonConfiguration.BODY_DIGEST;
    }

    // 받은 자리에서 옮긴다
    @Override
    void process() {
    }
}
