package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("결제 취소 — 운영 구현")
class PaymentCancelTest extends CancelContract {

    @Autowired
    PaymentCancelService paymentCancelService;

    @Override
    CancelResult cancel(CancelPaymentCommand command) {
        return paymentCancelService.cancel(command);
    }

    // 출발 구조에는 취소 보정이 없다
    @Override
    void reconcile() {
    }
}
