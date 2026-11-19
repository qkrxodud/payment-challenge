package com.example.payment.application.payment;

import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
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
 * 7장부터 결제 줄을 잠그고 읽는다(결정 1). 승인 답 기록과 겹치면 나중 기록이 앞 기록이 남긴 결제를 보고 판단한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentWebhookRecorder {

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookRepository paymentWebhookRepository;
    private final PaymentHoldings paymentHoldings;
    private final PaymentEvents paymentEvents;
    private final Clock clock;

    // 그 사이 결제가 사라질 일은 없지만, 없으면 옮길 것도 없다.
    // 8장 결정 4부터 따라간 결과가 승인 실패(카드사 거절·만료)나 취소 상태면 같은 트랜잭션에서 잡아 둔 것을 돌려준다.
    // 9장부터 따라가 결제 결과가 정해졌으면 주문 서비스에 알린다
    @Transactional
    public void follow(String orderId, GatewayPayment gatewayPayment) {
        paymentRepository.findByOrderIdForUpdate(orderId)
                .ifPresent(payment -> follow(payment, gatewayPayment));
    }

    private void follow(Payment payment, GatewayPayment gatewayPayment) {
        PaymentStatus before = payment.getStatus();
        payment.follow(gatewayPayment, clock.instant());
        paymentHoldings.releaseIfClosed(payment, before);
        paymentEvents.publishIfChanged(payment, before);
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
