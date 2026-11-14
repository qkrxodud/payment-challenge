package com.example.payment.domain.payment;

import com.example.payment.domain.payment.comparison.StatemachinePayment;
import org.junit.jupiter.api.DisplayName;

@DisplayName("결제 상태 바꾸기 — Spring Statemachine(비교 구현)")
class StatemachineTransitionTest extends PaymentTransitionContract {

    @Override
    protected PaymentHandle newPayment() {
        StatemachinePayment payment = StatemachinePayment.create();
        return new PaymentHandle() {
            @Override
            public PaymentStatus status() {
                return payment.getStatus();
            }

            @Override
            public void perform(PaymentAction action) {
                payment.perform(action);
            }
        };
    }
}
