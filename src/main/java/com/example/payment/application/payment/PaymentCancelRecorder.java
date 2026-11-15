package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.payment.GatewayCancel;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentCancel;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 취소 앞뒤의 기록을 각각 짧은 트랜잭션으로 남긴다(5장). PaymentRecorder 와 같은 이유로 취소 서비스와 다른 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
public class PaymentCancelRecorder {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public Payment find(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    // 카드사에 보내기 전에 취소를 받을 수 있는지 보고 취소할 금액을 정한다
    @Transactional(readOnly = true)
    public BigDecimal checkCancel(CancelPaymentCommand command) {
        return find(command.orderId()).checkCancel(command.cancelAmount(), command.cancelReason());
    }

    // 카드사가 취소한 결과를 취소 한 건으로 남긴다
    @Transactional
    public CancelResult recordCanceled(CancelPaymentCommand command, BigDecimal amount, GatewayCancel canceled) {
        Payment payment = find(command.orderId());
        PaymentCancel cancel = payment.recordCancel(command.idempotencyKey(), amount, command.cancelReason(), canceled);
        return CancelResults.of(payment, cancel);
    }
}
