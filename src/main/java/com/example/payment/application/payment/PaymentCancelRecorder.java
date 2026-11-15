package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.payment.CancelStatus;
import com.example.payment.domain.payment.Payment;
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

    // 카드사가 취소했으니 결제를 취소 상태로 바꾼다. 남은 금액은 이번 요청 금액만 빼서 계산한다
    @Transactional
    public CancelResult recordCanceled(String orderId, BigDecimal amount) {
        Payment payment = find(orderId);
        payment.cancel();
        return new CancelResult(orderId, amount, CancelStatus.DONE, payment.getAmount().subtract(amount));
    }
}
