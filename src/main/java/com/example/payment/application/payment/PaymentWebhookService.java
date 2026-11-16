package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ReceiveWebhookCommand;
import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 카드사 웹훅 받기(6장). 결제 상태 알림은 "이 결제를 다시 보라"는 신호로만 쓴다 — 본문의 상태를 믿지 않고
 * 카드사 결제 조회(GET /v1/payments/orders/{orderId})로 지금 상태를 다시 읽어 맞춘다(결정 1).
 * 모르는 주문의 알림에는 다시 볼 결제가 없으니 아무것도 하지 않고 받았다고 답한다.
 * 카드사 조회는 트랜잭션 밖에서 하고, 조회 결과를 옮기는 것만 짧은 트랜잭션으로 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentWebhookRecorder paymentWebhookRecorder;

    public void receive(ReceiveWebhookCommand command) {
        if (isNothingToFollow(command)) {
            return;
        }
        GatewayPayment gatewayPayment = paymentGateway.findPayment(command.orderId());
        paymentWebhookRecorder.follow(command.orderId(), gatewayPayment);
    }

    // 결제 상태 알림이 아니거나, 우리가 모르는 주문의 알림
    private boolean isNothingToFollow(ReceiveWebhookCommand command) {
        if (command.isPaymentStatusChange()) {
            return paymentRepository.findByOrderId(command.orderId()).isEmpty();
        }
        return true;
    }
}
