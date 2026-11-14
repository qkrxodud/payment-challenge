package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.SelfInvocationConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(SelfInvocationConfirmService.class)
@DisplayName("결제 승인 — 비교: 같은 클래스 안에서 기록 메서드 부르기")
class SelfInvocationConfirmTest extends PaymentConfirmContract {

    @Autowired
    SelfInvocationConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }
}
