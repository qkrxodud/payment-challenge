package com.example.payment.presentation.webhook.comparison;

import com.example.payment.application.payment.comparison.InRequestWebhookService;
import com.example.payment.presentation.webhook.dto.PaymentWebhookRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 비교 구현 E 의 웹훅 주소 */
@RestController
@RequiredArgsConstructor
public class InRequestWebhookController {

    static final String PATH = "/comparison/webhooks/in-request";

    private final InRequestWebhookService service;

    @PostMapping(PATH)
    public void receive(@RequestBody PaymentWebhookRequest request) {
        service.receive(request.toCommand());
    }
}
