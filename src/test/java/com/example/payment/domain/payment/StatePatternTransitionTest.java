package com.example.payment.domain.payment;

import com.example.payment.domain.payment.comparison.StatePatternPayment;
import org.junit.jupiter.api.DisplayName;

@DisplayName("결제 상태 바꾸기 — State 패턴(비교 구현)")
class StatePatternTransitionTest extends PaymentTransitionContract {

    @Override
    protected PaymentHandle newPayment() {
        StatePatternPayment payment = StatePatternPayment.create();
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
