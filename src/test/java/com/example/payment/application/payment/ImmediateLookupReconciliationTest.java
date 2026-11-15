package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.ImmediateLookupConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(ImmediateLookupConfirmService.class)
@DisplayName("결과 모름 보정 — 비교 C: 시간 초과 직후 요청 안에서 바로 조회")
class ImmediateLookupReconciliationTest extends ReconciliationContract {

    @Autowired
    ImmediateLookupConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }

    @Override
    void reconcile() {
        // 요청 밖에서는 다시 보지 않는다
    }
}
