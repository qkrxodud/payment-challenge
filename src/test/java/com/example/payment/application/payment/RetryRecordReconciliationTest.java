package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.RetryRecordConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(RetryRecordConfirmService.class)
@DisplayName("결과 모름 보정 — 비교 D: 기록 실패를 요청 안에서 다시 시도하고 결과 모름만 보정")
class RetryRecordReconciliationTest extends ReconciliationContract {

    @Autowired
    RetryRecordConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }

    @Override
    void reconcile() {
        service.reconcile(afterWaiting());
    }
}
