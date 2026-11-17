package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.WaitForFirstConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(WaitForFirstConfirmService.class)
@DisplayName("카드사 답을 기다리는 결제에 다시 온 승인 — 비교: 첫 요청이 끝날 때까지 기다렸다가 같은 결과로 답함")
class WaitForFirstConfirmTest extends InProgressConfirmContract {

    @Autowired
    WaitForFirstConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }
}
