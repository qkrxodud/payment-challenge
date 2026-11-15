package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("결과 모름 보정 — 운영 구현")
class PaymentReconciliationTest extends ReconciliationContract {

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return paymentConfirmService.confirm(command);
    }

    @Override
    void reconcile() {
        // 4장 출발 구조: 서버에는 카드사 기록에 맞추는 보정이 없다
    }
}
