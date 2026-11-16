package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentWebhookRecorder;
import com.example.payment.application.payment.dto.ReceiveWebhookCommand;
import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 비교 구현 E(6장 결정 4): 받은 요청 안에서 카드사 조회와 반영까지 끝내고 답한다. 조회·반영은 운영 구현과 같고(같은 옮기기),
 * 받은 알림을 남기지 않는다. 카드사 조회에 답이 없거나 오류면 그 오류(504, 502)로 답하고, 카드사가 같은 알림을 다시 보낸다.
 */
@Service
@RequiredArgsConstructor
public class InRequestWebhookService {

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

    private boolean isNothingToFollow(ReceiveWebhookCommand command) {
        if (command.isPaymentStatusChange()) {
            return paymentRepository.findByOrderId(command.orderId()).isEmpty();
        }
        return true;
    }
}
