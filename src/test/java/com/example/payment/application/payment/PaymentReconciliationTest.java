package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.payment.MockPgServer;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.error.exception.PaymentGatewayFailedException;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("결과 모름 보정 — 운영 구현")
class PaymentReconciliationTest extends ReconciliationContract {

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentReconciler paymentReconciler;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return paymentConfirmService.confirm(command);
    }

    @Override
    void reconcile() {
        paymentReconciler.reconcile(afterWaiting());
    }

    @Test
    @DisplayName("승인을 요청한 지 보정 대기 시간이 지나지 않은 결과 모름 결제는 카드사가 아직 처리 중일 수 있어 보정하지 않는다")
    void recentUnknownPaymentIsNotReconciled() {
        String orderId = prepare();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> confirm(request(orderId)));

        // 카드사는 아직 승인을 처리하는 중이다. 지금 조회하면 기록이 없다는 답이 온다
        paymentReconciler.reconcile(Instant.now());

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVAL_UNKNOWN);
        awaitCardCompany(orderId);
    }

    @Test
    @DisplayName("카드사가 서버 오류로 답하고 승인을 남기지 않은 결제는 보정 대기 시간이 지난 뒤 보정에서 승인 실패로 닫힌다")
    void gatewayFailureIsClosedByReconciliation() {
        String orderId = prepare();
        mockPg.fail();
        Throwable failure = catchThrowable(() -> confirm(request(orderId)));
        mockPg.reset();

        paymentReconciler.reconcile(afterWaiting());

        assertThat(failure).isInstanceOf(PaymentGatewayFailedException.class);
        assertThat(mockPg.paymentState(orderId)).isEqualTo(MockPgServer.NONE);
        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    @Test
    @DisplayName("카드사가 승인한 뒤 가맹점 관리 화면에서 전액 취소한 결과 모름 결제는 보정 뒤 승인 취소로 닫힌다")
    void unknownPaymentCanceledAtCardCompanyBecomesApprovalCanceled() {
        String orderId = prepare();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> confirm(request(orderId)));
        awaitCardCompany(orderId);
        mockPg.reset();
        mockPg.cancelAtCardCompany(orderId);

        reconcile();

        assertMatchesCardCompany(orderId);
    }

    @Test
    @DisplayName("카드사가 승인한 뒤 부분 취소한 결과 모름 결제는 보정 뒤 승인되고 남은 금액이 카드사 잔액과 같다")
    void unknownPaymentPartiallyCanceledAtCardCompanyKeepsBalance() {
        String orderId = prepare();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> confirm(request(orderId)));
        awaitCardCompany(orderId);
        mockPg.reset();
        mockPg.cancelAtCardCompany(orderId, new BigDecimal("5000"));

        reconcile();

        assertThat(balanceOf(orderId)).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    @Test
    @DisplayName("카드사 승인 뒤 기록에 실패해 인증 상태에 남은 결제도 카드사에서 전액 취소됐으면 보정 뒤 승인 취소로 닫힌다")
    void authenticatedPaymentCanceledAtCardCompanyBecomesApprovalCanceled() {
        String orderId = prepare();
        blockApprovalRecord();
        catchThrowable(() -> confirm(request(orderId)));
        allowApprovalRecord();
        mockPg.cancelAtCardCompany(orderId);

        reconcile();

        assertMatchesCardCompany(orderId);
    }

    private BigDecimal balanceOf(String orderId) {
        return paymentQueryService.findCancels(orderId).stream()
                .filter(cancel -> cancel.cancelStatus().isDone())
                .map(cancel -> cancel.cancelAmount())
                .reduce(new BigDecimal("15000"), BigDecimal::subtract);
    }
}
