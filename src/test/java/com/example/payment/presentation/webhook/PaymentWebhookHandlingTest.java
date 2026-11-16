package com.example.payment.presentation.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.payment.application.payment.PaymentReconciler;
import com.example.payment.application.payment.PaymentWebhookProcessor;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("카드사 웹훅 처리 — 운영 구현")
class PaymentWebhookHandlingTest extends WebhookContract {

    @Autowired
    PaymentWebhookProcessor paymentWebhookProcessor;

    @Autowired
    PaymentReconciler paymentReconciler;

    @Override
    String webhookPath() {
        return "/api/v1/webhooks/tosspayments";
    }

    // 결정 4부터 받은 알림은 처리기가 맞춘다
    @Override
    void process() {
        paymentWebhookProcessor.process();
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

    @Test
    @DisplayName("카드사에서 승인 뒤 취소된 결과 모름 결제는 주기 보정이 먼저 돌고 알림이 나중에 처리돼도 카드사와 같아진다")
    void reconciliationFirstThenWebhookMatchesCardCompany() {
        String orderId = unknownPaymentCanceledAtCardCompany();

        paymentReconciler.reconcile(afterWaiting());
        mockPg.releaseWebhooks(false);
        awaitWebhooks();
        process();

        assertMatchesCardCompany(orderId);
    }

    @Test
    @DisplayName("카드사에서 승인 뒤 취소된 결과 모름 결제는 알림이 먼저 처리되고 주기 보정이 나중에 돌아도 카드사와 같아진다")
    void webhookFirstThenReconciliationMatchesCardCompany() {
        String orderId = unknownPaymentCanceledAtCardCompany();

        mockPg.releaseWebhooks(false);
        awaitWebhooks();
        process();
        paymentReconciler.reconcile(afterWaiting());

        assertMatchesCardCompany(orderId);
    }

    @Test
    @DisplayName("주기 보정과 알림 처리가 둘 다 돌아도 카드사 취소는 한 번만 기록된다")
    void reconciliationAndWebhookRecordCancelOnce() {
        String orderId = unknownPaymentCanceledAtCardCompany();

        paymentReconciler.reconcile(afterWaiting());
        mockPg.releaseWebhooks(false);
        awaitWebhooks();
        process();

        assertThat(paymentQueryService.findCancels(orderId)).hasSize(1);
    }

    // 승인 답을 못 받은 결제를 카드사가 승인했고 그 뒤 가맹점 관리 화면에서 전액 취소했다. 알림은 쌓아 둔다
    private String unknownPaymentCanceledAtCardCompany() {
        String orderId = prepare();
        sendWebhooks();
        mockPg.holdWebhooks();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> paymentConfirmService.confirm(confirmRequest(orderId)));
        awaitCardCompany(orderId);
        mockPg.reset();
        mockPg.cancelAtCardCompany(orderId);
        return orderId;
    }

    // 보정 대기 시간(10초)이 지난 때
    private static Instant afterWaiting() {
        return Instant.now().plusSeconds(11);
    }
}
