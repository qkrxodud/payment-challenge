package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.error.exception.PaymentGatewayFailedException;
import com.example.payment.domain.payment.CancelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("결제 취소 — 운영 구현")
class PaymentCancelTest extends CancelContract {

    @Autowired
    PaymentCancelService paymentCancelService;

    @Autowired
    PaymentCancelReconciler paymentCancelReconciler;

    @Override
    CancelResult cancel(CancelPaymentCommand command) {
        return paymentCancelService.cancel(command);
    }

    @Override
    void reconcile() {
        paymentCancelReconciler.reconcile(afterWaiting());
    }

    @Test
    @DisplayName("카드사가 취소를 거절하면 그 취소는 취소 실패로 남는다")
    void rejectedCancelIsKeptAsFailed() {
        // given: 우리 기록에는 남은 금액이 있지만 카드사에서는 이미 전액 취소된 결제
        String orderId = approvedOrder();
        mockPg.cancelAtCardCompany(orderId);

        // when
        CancelResult result = cancel(request(orderId, "cancel-1", PART));

        // then
        assertThat(result.cancelStatus()).isEqualTo(CancelStatus.FAILED);
    }

    @Test
    @DisplayName("카드사가 거절한 취소는 남은 금액을 줄이지 않는다")
    void rejectedCancelKeepsBalance() {
        String orderId = approvedOrder();
        mockPg.cancelAtCardCompany(orderId);

        CancelResult result = cancel(request(orderId, "cancel-1", PART));

        assertThat(result.balanceAmount()).isEqualByComparingTo(PREPARED);
    }

    @Test
    @DisplayName("보정 중 카드사에 보낸 확인 요청에도 답이 없으면 결과를 모르는 취소를 그대로 둔다")
    void unansweredInquiryLeavesUnknownCancel() {
        String orderId = approvedOrder();
        mockPg.answerFollowUpsAfter(LATE);
        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));
        awaitCardCompany(orderId);

        reconcile();

        assertThat(lastCancelOf(orderId).cancelStatus()).isEqualTo(CancelStatus.UNKNOWN);
    }

    @Test
    @DisplayName("카드사가 취소에 서버 오류로 답하면 그 취소는 카드사 답을 기다리는 요청으로 남는다")
    void gatewayFailureLeavesRequestedCancel() {
        String orderId = approvedOrder();
        mockPg.failFollowUps();

        Throwable failure = catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));

        assertThat(failure).isInstanceOf(PaymentGatewayFailedException.class);
        assertThat(lastCancelOf(orderId).cancelStatus()).isEqualTo(CancelStatus.REQUESTED);
    }

    @Test
    @DisplayName("카드사가 서버 오류로 답하고 취소하지 않은 취소는 보정 대기 시간이 지난 뒤 보정에서 취소 실패로 닫힌다")
    void gatewayFailureIsClosedByReconciliation() {
        String orderId = approvedOrder();
        mockPg.failFollowUps();
        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));
        mockPg.reset();

        reconcile();

        assertThat(mockPg.cancelCount(orderId)).isZero();
        assertThat(lastCancelOf(orderId).cancelStatus()).isEqualTo(CancelStatus.FAILED);
    }

    @Test
    @DisplayName("카드사가 서버 오류로 답한 취소는 보정 뒤 남은 금액이 카드사 잔액과 같다")
    void gatewayFailureMatchesCardCompanyAfterReconciliation() {
        String orderId = approvedOrder();
        mockPg.failFollowUps();
        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));
        mockPg.reset();

        reconcile();

        assertThat(lastCancelOf(orderId).balanceAmount()).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    @Test
    @DisplayName("보정 중 카드사 조회가 서버 오류로 끝나면 결과를 모르는 취소를 그대로 둔다")
    void inquiryFailureLeavesUnknownCancel() {
        String orderId = approvedOrder();
        mockPg.answerFollowUpsAfter(LATE);
        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));
        awaitCardCompany(orderId);
        mockPg.failFollowUps();

        reconcile();

        assertThat(lastCancelOf(orderId).cancelStatus()).isEqualTo(CancelStatus.UNKNOWN);
    }

    @Test
    @DisplayName("보정 중 조회 오류로 남은 취소는 다음 보정에서 다시 확인해 카드사 잔액과 맞춘다")
    void inquiryFailureIsRetriedNextTime() {
        String orderId = approvedOrder();
        mockPg.answerFollowUpsAfter(LATE);
        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));
        awaitCardCompany(orderId);
        mockPg.failFollowUps();
        reconcile();
        mockPg.reset();

        reconcile();

        assertThat(lastCancelOf(orderId).balanceAmount()).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }
}
