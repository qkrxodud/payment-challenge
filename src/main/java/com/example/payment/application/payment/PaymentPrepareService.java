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

    @Transactional
    public PaymentResult prepare(PreparePaymentCommand command) {
        Payment payment = paymentRepository.save(Payment.create(command.orderId(), command.amount()));
        return PaymentResult.from(payment);
    }
}
