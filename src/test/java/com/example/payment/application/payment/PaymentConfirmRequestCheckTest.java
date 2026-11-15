package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("승인 요청 확인 — 운영 구현")
class PaymentConfirmRequestCheckTest extends ConfirmRequestCheckContract {

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return paymentConfirmService.confirm(command);
    }
}
