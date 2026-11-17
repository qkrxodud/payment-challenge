package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.UncaughtPrepareService;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(UncaughtPrepareService.class)
@DisplayName("같은 주문의 동시 준비 — 비교: 잡지 않음(6장까지, 주문번호 중복 거부를 그대로 알림)")
class UncaughtPrepareTest extends PrepareConcurrencyContract {

    @Autowired
    UncaughtPrepareService service;

    @Override
    PaymentResult prepare(PreparePaymentCommand command) {
        return service.prepare(command);
    }
}
