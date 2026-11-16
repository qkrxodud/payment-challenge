package com.example.payment.application.payment;

import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.webhook.PaymentWebhook;
import com.example.payment.domain.webhook.PaymentWebhookRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드사에서 다시 읽은 결제를 우리 결제에 옮기는 짧은 트랜잭션(6장). 웹훅 서비스와 다른 빈으로 둔 이유는 PaymentRecorder 와 같다.
 * 결정 4부터 받아 둔 알림의 처리 결과도 같은 트랜잭션에 남긴다 — 결제를 맞춘 것과 알림을 닫은 것이 함께 반영된다.
 */
@Component
@RequiredArgsConstructor
public class PaymentWebhookRecorder {

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookRepository paymentWebhookRepository;
    private final Clock clock;

    // 그 사이 결제가 사라질 일은 없지만, 없으면 옮길 것도 없다
    @Transactional
    public void follow(String orderId, GatewayPayment gatewayPayment) {
        paymentRepository.findByOrderId(orderId)
                .ifPresent(payment -> payment.follow(gatewayPayment, clock.instant()));
    }

    // 결제를 맞추고, 그 결제를 다시 보라던 알림들을 닫는다
    @Transactional
    public void follow(String orderId, List<Long> webhookIds, GatewayPayment gatewayPayment) {
        follow(orderId, gatewayPayment);
        webhooks(webhookIds).forEach(webhook -> webhook.process(clock.instant()));
    }

    @Transactional
    public void ignore(List<Long> webhookIds) {
        webhooks(webhookIds).forEach(webhook -> webhook.ignore(clock.instant()));
    }

    @Transactional
    public void recordFailedLookup(List<Long> webhookIds) {
        webhooks(webhookIds).forEach(PaymentWebhook::failLookup);
    }

    private List<PaymentWebhook> webhooks(List<Long> webhookIds) {
        return paymentWebhookRepository.findAllById(webhookIds);
    }
}
