package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;

/** 비교 구현 A: 금액을 확인하지 않고 고객이 보낸 금액을 그대로 PG 에 보낸다(3장 출발 구조의 금액 처리) */
@RequiredArgsConstructor
public class TrustRequestAmountConfirmService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = transactionTemplate.execute(status -> {
            Payment found = find(command.orderId());
            if (found.isConfirmedWith(command.paymentKey())) {
                return found;
            }
            found.authenticate(command.paymentKey());
            return found;
        });

        if (payment.isConfirmedWith(command.paymentKey())) {
            return PaymentResult.from(payment);
        }

        PaymentApproval approval = paymentGateway.approve(command.paymentKey(), payment.getOrderId(), command.amount());

        return transactionTemplate.execute(status -> {
            Payment found = find(command.orderId());
            if (approval.isDeclined()) {
                found.failApproval();
                return PaymentResult.from(found);
            }
            found.approve();
            return PaymentResult.from(found);
        });
    }

    private Payment find(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }
}
