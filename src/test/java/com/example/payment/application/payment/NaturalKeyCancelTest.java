package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NaturalKeyCancelService;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(NaturalKeyCancelService.class)
@DisplayName("결제 취소 — 비교 A: 같은 금액·사유를 같은 요청으로 봄(자연 키)")
class NaturalKeyCancelTest extends CancelContract {

    @Autowired
    NaturalKeyCancelService service;

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
