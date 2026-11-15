package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.PaymentStatus;
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
}
