package com.example.payment.application.payment;

import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.webhook.PaymentWebhook;
import com.example.payment.domain.webhook.PaymentWebhookRepository;
import com.example.payment.global.error.exception.PaymentGatewayFailedException;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 받아 둔 웹훅을 처리한다(6장 결정 4). 아직 처리하지 않은 알림을 받은 순서대로 100건까지 읽어 주문마다 묶고,
 * 주문마다 카드사 조회를 한 번 해서 결제를 맞춘다 — 같은 주문의 알림이 여러 건이어도 지금 상태는 하나라 한 번 읽으면 된다.
 * 모르는 주문의 알림은 카드사에 묻지 않고 닫는다. 카드사 조회에 실패하면 알림을 받은 상태로 남겨 다음 처리 때 다시 본다.
 * 주문 하나를 맞추다 실패해도 나머지 주문은 이어서 맞춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentWebhookProcessor {

    private final PaymentWebhookRepository paymentWebhookRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentWebhookRecorder paymentWebhookRecorder;

    public void process() {
        Map<String, List<Long>> byOrder = paymentWebhookRepository.findReceived().stream()
                .collect(Collectors.groupingBy(PaymentWebhook::getOrderId, LinkedHashMap::new,
                        Collectors.mapping(PaymentWebhook::getId, Collectors.toList())));
        byOrder.forEach(this::process);
    }

    private void process(String orderId, List<Long> webhookIds) {
        if (paymentRepository.findByOrderId(orderId).isEmpty()) {
            paymentWebhookRecorder.ignore(webhookIds);
            return;
        }
        try {
            GatewayPayment gatewayPayment = paymentGateway.findPayment(orderId);
            paymentWebhookRecorder.follow(orderId, webhookIds, gatewayPayment);
        } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException unanswered) {
            paymentWebhookRecorder.recordFailedLookup(webhookIds);
            log.warn("Could not look up a payment for a webhook ({}). It will be processed again next time.",
                    unanswered.getClass().getSimpleName());
        }
    }
}
