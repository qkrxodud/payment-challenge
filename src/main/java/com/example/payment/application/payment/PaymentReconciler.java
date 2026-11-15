package com.example.payment.application.payment;

import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.PaymentGatewayFailedException;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 결과 모름 결제를 카드사 기록에 맞춘다(4장). 주문번호로 카드사에 결제를 조회하고 그 결과대로 승인 또는 승인 실패로 닫는다.
 * 카드사에 기록이 없으면 승인 요청이 닿지 않은 것으로 보고 승인 실패로 닫는다.
 * 조회에도 답이 없으면 상태를 바꾸지 않는다. 결과 모름으로 남아 있으니 다음 보정에서 다시 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciler {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentRecorder paymentRecorder;

    public void reconcile() {
        paymentRepository.findAllByStatus(PaymentStatus.APPROVAL_UNKNOWN)
                .forEach(payment -> reconcile(payment.getOrderId()));
    }

    // 결제 하나를 맞추다 실패해도 나머지 결제는 이어서 맞춘다
    private void reconcile(String orderId) {
        try {
            paymentRecorder.recordInquiry(orderId, paymentGateway.inquire(orderId));
        } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException unanswered) {
            log.warn("Could not confirm a payment result with the gateway ({}). It will be checked again next time.",
                    unanswered.getClass().getSimpleName());
        }
    }
}
