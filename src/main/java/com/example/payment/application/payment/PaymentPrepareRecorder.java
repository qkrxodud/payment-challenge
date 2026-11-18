package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 준비 기록(7장 결정 3). 준비 서비스가 주문번호 중복 거부를 잡고 새 트랜잭션에서 다시 읽으려고 다른 빈으로 둔다 —
 * 중복 거부가 난 트랜잭션은 이미 되돌리기로 표시돼 그 안에서는 다시 읽어 답할 수 없다.
 */
@Component
@RequiredArgsConstructor
public class PaymentPrepareRecorder {

    private final PaymentRepository paymentRepository;
    private final PaymentOrders paymentOrders;

    // 같은 주문번호가 같은 금액으로 다시 오면 이미 있는 결제를 지금 상태 그대로 돌려준다. 금액이 다르면 거부한다(3장)
    @Transactional
    public PaymentResult prepare(PreparePaymentCommand command) {
        return paymentRepository.findByOrderId(command.orderId())
                .map(existing -> repeat(existing, command))
                .orElseGet(() -> PaymentResult.from(paymentRepository.save(newPayment(command))));
    }

    // 8장: 상품을 담은 준비는 주문으로 결제를 만든다. 상품이 없으면 7장까지처럼 주문번호와 금액만으로 만든다
    private Payment newPayment(PreparePaymentCommand command) {
        if (command.hasItems()) {
            return paymentOrders.order(command);
        }
        return Payment.create(command.orderId(), command.amount());
    }

    // 먼저 남긴 같은 주문번호의 결제를 다시 읽어 3장 규칙대로 답한다. 없으면 비어 있다
    @Transactional(readOnly = true)
    public Optional<PaymentResult> prepareAgain(PreparePaymentCommand command) {
        return paymentRepository.findByOrderId(command.orderId())
                .map(existing -> repeat(existing, command));
    }

    private static PaymentResult repeat(Payment existing, PreparePaymentCommand command) {
        existing.verifyPreparedAmount(command.amount());
        return PaymentResult.from(existing);
    }
}
