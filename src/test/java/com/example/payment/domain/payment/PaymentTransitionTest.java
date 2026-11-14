package com.example.payment.domain.payment;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;

@DisplayName("결제 상태 바꾸기 — 허용 전이 표")
class PaymentTransitionTest extends PaymentTransitionContract {

    @Override
    protected PaymentHandle newPayment() {
        Payment payment = Payment.create("order-20261114-0001", new BigDecimal("10000"));
        return new PaymentHandle() {
            @Override
            public PaymentStatus status() {
                return payment.getStatus();
            }

            @Override
            public void perform(PaymentAction action) {
                switch (action) {
                    case AUTHENTICATE -> payment.authenticate();
                    case FAIL_AUTHENTICATION -> payment.failAuthentication();
                    case APPROVE -> payment.approve();
                    case FAIL_APPROVAL -> payment.failApproval();
                    case EXPIRE -> payment.expire();
                    case CAPTURE -> payment.capture();
                    case CANCEL -> payment.cancel();
                }
            }
        };
    }
}
