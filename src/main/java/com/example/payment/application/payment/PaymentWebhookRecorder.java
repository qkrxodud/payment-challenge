package com.example.payment.application.payment;

import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드사에서 다시 읽은 결제를 우리 결제에 옮기는 짧은 트랜잭션(6장). 웹훅 서비스와 다른 빈으로 둔 이유는 PaymentRecorder 와 같다.
 */
@Component
@RequiredArgsConstructor
public class PaymentWebhookRecorder {

    private final PaymentRepository paymentRepository;

    // 그 사이 결제가 사라질 일은 없지만, 없으면 옮길 것도 없다
    @Transactional
    public void follow(String orderId, GatewayPayment gatewayPayment) {
        paymentRepository.findByOrderId(orderId)
                .ifPresent(payment -> payment.report(gatewayPayment.getStatus()));
    }
}
