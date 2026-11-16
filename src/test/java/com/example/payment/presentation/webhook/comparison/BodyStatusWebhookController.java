package com.example.payment.presentation.webhook.comparison;

import com.example.payment.application.payment.PaymentWebhookRecorder;
import com.example.payment.presentation.webhook.comparison.dto.ReportedWebhook;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비교 구현 A(6장 결정 1): 본문의 상태를 그대로 믿는다. 카드사에 다시 묻지 않고, 본문에 적힌 결제(상태·취소 이력)를
 * 운영 구현과 같은 옮기기(PaymentWebhookRecorder)에 넘긴다. 받은 자리에서 옮기고 답한다.
 */
@RestController
@RequiredArgsConstructor
public class BodyStatusWebhookController {

    static final String PATH = "/comparison/webhooks/body-status";

    private final PaymentWebhookRecorder paymentWebhookRecorder;

    @PostMapping(PATH)
    public void receive(@RequestBody ReportedWebhook webhook) {
        if (webhook.isPaymentStatusChange()) {
            paymentWebhookRecorder.follow(webhook.orderId(), webhook.reported());
        }
    }
}
