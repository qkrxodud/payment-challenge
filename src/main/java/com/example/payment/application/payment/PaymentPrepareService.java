package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentPrepareService {

    private final PaymentRepository paymentRepository;

    // 같은 주문번호가 같은 금액으로 다시 오면 이미 있는 결제를 지금 상태 그대로 돌려준다. 금액이 다르면 거부한다
    @Transactional
    public PaymentResult prepare(PreparePaymentCommand command) {
        return paymentRepository.findByOrderId(command.orderId())
                .map(existing -> repeat(existing, command))
                .orElseGet(() -> PaymentResult.from(
                        paymentRepository.save(Payment.create(command.orderId(), command.amount()))));
    }

    private static PaymentResult repeat(Payment existing, PreparePaymentCommand command) {
        existing.verifyPreparedAmount(command.amount());
        return PaymentResult.from(existing);
    }
}
