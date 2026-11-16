package com.example.payment.presentation.webhook.comparison;

import com.example.payment.application.payment.comparison.RejectOffTableWebhookService;
import com.example.payment.presentation.webhook.dto.PaymentWebhookRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 비교 구현 B 의 웹훅 주소 */
@RestController
@RequiredArgsConstructor
public class RejectOffTableWebhookController {

    static final String PATH = "/comparison/webhooks/reject-off-table";

    private final RejectOffTableWebhookService service;

    @PostMapping(PATH)
    public void receive(@RequestBody PaymentWebhookRequest request) {
        service.receive(request.toCommand());
    }
}
