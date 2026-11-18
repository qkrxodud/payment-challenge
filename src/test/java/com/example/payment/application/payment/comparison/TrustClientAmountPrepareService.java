package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 구현(8장 결정 1): 고객이 보낸 금액을 믿고 결제에 남긴다. 승인 때는 3장 규칙대로 결제에 남은 금액과만 비교한다.
 * 금액만 보는 비교라 담긴 상품·쿠폰·포인트는 남기지 않는다(같은 주문번호 다시 온 준비도 다루지 않는다).
 */
@RequiredArgsConstructor
public class TrustClientAmountPrepareService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResult prepare(PreparePaymentCommand command) {
        return PaymentResult.from(paymentRepository.save(Payment.create(command.orderId(), command.amount())));
    }
}
