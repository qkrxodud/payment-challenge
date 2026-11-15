package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.EqualsAmountConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(EqualsAmountConfirmService.class)
@DisplayName("승인 요청 확인 — 비교 B: 금액을 equals 로 비교")
class EqualsAmountConfirmTest extends ConfirmRequestCheckContract {

    @Autowired
    EqualsAmountConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }
}
