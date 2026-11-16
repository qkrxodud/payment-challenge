package com.example.payment.application.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 받아 둔 웹훅 처리를 정해진 주기마다 돌린다(6장 결정 4). 앞 처리가 끝난 뒤 payment.webhook.interval 만큼 쉬고 다시 돈다.
 * 테스트는 처리를 직접 부르므로 payment.webhook.enabled=false 로 끈다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.webhook", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentWebhookScheduler {

    private final PaymentWebhookProcessor paymentWebhookProcessor;

    @Scheduled(fixedDelayString = "${payment.webhook.interval}")
    public void process() {
        paymentWebhookProcessor.process();
    }
}
