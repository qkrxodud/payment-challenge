package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.GatewayFirstCancelService;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(GatewayFirstCancelService.class)
@DisplayName("결제 취소 — 비교 C: 카드사 먼저 부르고 답을 받은 뒤에 기록함")
class GatewayFirstCancelTest extends CancelContract {

    @Autowired
    GatewayFirstCancelService service;

    @Autowired
    PaymentCancelReconciler paymentCancelReconciler;

    @Override
    CancelResult cancel(CancelPaymentCommand command) {
        return service.cancel(command);
    }

    // 보정은 운영 구현과 같다. 다만 보정할 기록이 남지 않는다
    @Override
    void reconcile() {
        paymentCancelReconciler.reconcile(afterWaiting());
    }
}
