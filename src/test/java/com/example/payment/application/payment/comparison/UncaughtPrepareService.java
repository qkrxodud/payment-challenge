package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 구현(7장 결정 3): 잡지 않음(6장까지). 한 트랜잭션에서 주문번호로 찾고 없으면 남긴다. 동시에 온 같은 주문의 나중 준비는
 * 주문번호 중복 거부를 그대로 알린다(전역 오류 처리기가 내부 오류 500 으로 답한다)
 */
@RequiredArgsConstructor
public class UncaughtPrepareService {

    private final PaymentRepository paymentRepository;

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
