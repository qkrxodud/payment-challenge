package com.example.payment.presentation.webhook;

import com.example.payment.application.payment.PaymentWebhookService;
import com.example.payment.presentation.webhook.dto.PaymentWebhookRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 6장: 카드사(토스페이먼츠)가 결제 상태가 바뀔 때 보내는 웹훅을 받는다. 200 으로 답해야 카드사가 다시 보내지 않는다 */
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping("/api/v1/webhooks/tosspayments")
    public void receive(@RequestBody PaymentWebhookRequest request) {
        paymentWebhookService.receive(request.toCommand());
    }
}
