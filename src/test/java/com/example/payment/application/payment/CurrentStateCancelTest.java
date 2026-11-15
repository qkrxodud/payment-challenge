package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.CurrentStateCancelService;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(CurrentStateCancelService.class)
@DisplayName("결제 취소 — 비교 B: 요청을 기록하지 않고 지금 결제 상태로 답함")
class CurrentStateCancelTest extends CancelContract {

    @Autowired
    CurrentStateCancelService service;

    @Autowired
    PaymentCancelReconciler paymentCancelReconciler;

    @Override
    CancelResult cancel(CancelPaymentCommand command) {
        return service.cancel(command);
    }

    // 같은 요청을 가리는 방법만 다르고 보정은 운영 구현과 같다
    @Override
    void reconcile() {
        paymentCancelReconciler.reconcile(afterWaiting());
    }
}
