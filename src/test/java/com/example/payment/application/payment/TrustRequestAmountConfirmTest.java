package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.TrustRequestAmountConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(TrustRequestAmountConfirmService.class)
@DisplayName("승인 요청 확인 — 비교 A: 요청 금액을 그대로 PG에 보냄")
class TrustRequestAmountConfirmTest extends ConfirmRequestCheckContract {

    @Autowired
    TrustRequestAmountConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }
}
