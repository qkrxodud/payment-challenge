package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.DeferredHoldRecorder;
import com.example.payment.application.payment.comparison.DeferredHoldings;
import com.example.payment.application.payment.comparison.HoldAtPrepareService;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import({DeferredHoldings.class, DeferredHoldRecorder.class, HoldAtPrepareService.class})
@DisplayName("재고를 줄이는 때 — 비교: 준비할 때")
class HoldAtPrepareTimingTest extends HoldTimingContract {

    @Autowired
    HoldAtPrepareService service;

    @Override
    PaymentResult prepare(PreparePaymentCommand command) {
        return service.prepare(command);
    }
}
