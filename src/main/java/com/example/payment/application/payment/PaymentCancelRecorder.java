package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelAttempt;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.payment.CancelRequest;
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

    // 같은 Idempotency-Key 로 이미 받은 취소면 그 결과를, 아니면 카드사에 보낼 금액을 정한다
    @Transactional(readOnly = true)
    public CancelAttempt prepare(CancelPaymentCommand command) {
        Payment payment = find(command.orderId());
        CancelRequest request = requestOf(command);
        return payment.findRepeatedCancel(request)
                .map(repeated -> CancelAttempt.repeat(CancelResults.of(payment, repeated)))
                .orElseGet(() -> CancelAttempt.send(payment.getPaymentKey(), payment.checkCancel(request)));
    }

    // 카드사가 취소한 결과를 취소 한 건으로 남긴다
    @Transactional
    public CancelResult recordCanceled(CancelPaymentCommand command, BigDecimal amount, GatewayCancel canceled) {
        Payment payment = find(command.orderId());
        PaymentCancel cancel = payment.recordCancel(requestOf(command), amount, canceled);
        return CancelResults.of(payment, cancel);
    }

    private Payment find(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    private static CancelRequest requestOf(CancelPaymentCommand command) {
        return CancelRequest.of(command.orderId(), command.idempotencyKey(), command.cancelAmount(), command.cancelReason());
    }
}
