package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.InTransactionNoRollbackConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(InTransactionNoRollbackConfirmService.class)
@DisplayName("결제 승인 — 비교 A': 하나로 묶되 거절로 실패해도 기록은 남김")
class InTransactionNoRollbackConfirmTest extends PaymentConfirmContract {

    @Autowired
    InTransactionNoRollbackConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }
}
