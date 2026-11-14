package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.TemplateConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(TemplateConfirmService.class)
@DisplayName("결제 승인 — 비교 B': TransactionTemplate 으로 나누기")
class TemplateConfirmTest extends PaymentConfirmContract {

    @Autowired
    TemplateConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }
}
