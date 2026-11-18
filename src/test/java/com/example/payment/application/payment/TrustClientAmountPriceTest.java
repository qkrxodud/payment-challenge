package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.TrustClientAmountPrepareService;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(TrustClientAmountPrepareService.class)
@DisplayName("결제 금액 — 비교: 고객이 보낸 금액을 믿음")
class TrustClientAmountPriceTest extends ServerPriceContract {

    @Autowired
    TrustClientAmountPrepareService service;

    @Override
    PaymentResult prepare(PreparePaymentCommand command) {
        return service.prepare(command);
    }
}
