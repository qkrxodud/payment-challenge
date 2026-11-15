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

/** 비교 구현 A: 출발 구조. 인증 기록, PG 호출, 결과 기록을 트랜잭션 하나로 묶고 거절은 예외로 알린다 */
@RequiredArgsConstructor
public class InTransactionConfirmService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    @Transactional
    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new PaymentNotFoundException(command.orderId()));
        payment.authenticate(command.paymentKey());

        PaymentApproval approval = paymentGateway.approve(command.paymentKey(), payment.getOrderId(), payment.getAmount());
        if (approval.isDeclined()) {
            payment.failApproval();
            throw new ApprovalDeclinedException(command.orderId());
        }
        payment.approve();
        return PaymentResult.from(payment);
    }
}
