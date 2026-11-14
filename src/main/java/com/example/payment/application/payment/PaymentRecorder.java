package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인 앞뒤의 기록을 각각 짧은 트랜잭션으로 남긴다.
 * PaymentConfirmService 와 다른 빈으로 둔 이유: 같은 클래스 안에서 부르면 @Transactional 이 적용되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentRecorder {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment authenticate(String orderId) {
        Payment payment = find(orderId);
        payment.authenticate();
        return payment;
    }

    @Transactional
    public PaymentResult recordApproval(String orderId, PaymentApproval approval) {
        Payment payment = find(orderId);
        if (approval.isDeclined()) {
            payment.failApproval();
            return PaymentResult.from(payment);
        }
        payment.approve();
        return PaymentResult.from(payment);
    }

    private Payment find(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }
}
