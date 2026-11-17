package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NamedLockPrepareService;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(NamedLockPrepareService.class)
@DisplayName("같은 주문의 동시 준비 — 비교: MySQL 이름 잠금(GET_LOCK)으로 주문번호마다 차례로 준비")
class NamedLockPrepareTest extends PrepareConcurrencyContract {

    @Autowired
    NamedLockPrepareService service;

    @Override
    PaymentResult prepare(PreparePaymentCommand command) {
        return service.prepare(command);
    }
}
