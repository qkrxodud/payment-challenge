package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("같은 주문의 동시 준비 — 운영 구현")
class PaymentPrepareConcurrencyTest extends PrepareConcurrencyContract {

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Override
    PaymentResult prepare(PreparePaymentCommand command) {
        return paymentPrepareService.prepare(command);
    }
}
