package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;

@DisplayName("카드사 답을 기다리는 결제에 다시 온 승인 — 운영 구현")
class PaymentInProgressConfirmTest extends InProgressConfirmContract {

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return paymentConfirmService.confirm(command);
    }
}
