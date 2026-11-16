package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ReceiveWebhookCommand;
import com.example.payment.domain.payment.GatewayStatus;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드사 웹훅 받기(6장 출발 구조). 결제 상태 알림이 오면 본문의 상태를 허용 전이 표대로 그 자리에서 결제에 옮긴다.
 * 모르는 주문이면 찾을 수 없다고, 표에 없는 전이면 지금 상태에서 할 수 없다고 답한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public void receive(ReceiveWebhookCommand command) {
        if (command.isPaymentStatusChange()) {
            find(command.orderId()).report(GatewayStatus.of(command.reportedStatus()));
        }
    }

    private Payment find(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }
}
