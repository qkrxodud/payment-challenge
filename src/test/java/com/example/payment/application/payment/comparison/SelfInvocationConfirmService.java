package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 구현 B 의 함정: 기록 메서드에 @Transactional 을 붙였지만 같은 클래스 안에서 this 로 부른다.
 * 스프링 프록시를 거치지 않으므로 두 기록 메서드에는 트랜잭션이 걸리지 않는다.
 */
@RequiredArgsConstructor
public class SelfInvocationConfirmService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = authenticate(command.orderId(), command.paymentKey());
        PaymentApproval approval = paymentGateway.approve(command.paymentKey(), payment.getOrderId(), payment.getAmount());
        return recordApproval(command.orderId(), approval);
    }

    @Transactional
    public Payment authenticate(String orderId, String paymentKey) {
        Payment payment = find(orderId);
        payment.authenticate(paymentKey);
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
