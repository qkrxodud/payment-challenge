package com.example.payment.presentation.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("카드사 웹훅 처리 — 운영 구현")
class PaymentWebhookHandlingTest extends WebhookContract {

    @Override
    String webhookPath() {
        return "/api/v1/webhooks/tosspayments";
    }

    // 출발 구조는 알림을 요청 안에서 반영한다
    @Override
    void process() {
    }

    @Test
    @DisplayName("카드사 조회에 실패한 알림은 다음 처리 때 다시 확인해 카드사 잔액과 맞춘다")
    void failedLookupIsRetriedNextTime() {
        // given: 카드사에서만 부분 취소가 일어났고, 알림을 처리할 때 카드사 조회가 서버 오류로 끝난다
        String orderId = approvedOrder();
        sendWebhooks();
        mockPg.failFollowUps();
        mockPg.cancelAtCardCompany(orderId, PART);
        awaitWebhooks();
        process();
        mockPg.reset();

        // when
        process();

        // then
        assertThat(balanceOf(orderId)).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }
}
