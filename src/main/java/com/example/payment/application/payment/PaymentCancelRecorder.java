package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelAttempt;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.payment.CancelOutcome;
import com.example.payment.domain.payment.CancelRequest;
import com.example.payment.domain.payment.GatewayCancel;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentCancel;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
    private final Clock clock;

    // 같은 Idempotency-Key 로 이미 받은 취소면 그 결과를 돌려준다. 새 취소면 받을 수 있는지 보고 카드사 답을 기다리는 취소로 남긴다
    @Transactional
    public CancelAttempt request(CancelPaymentCommand command) {
        Payment payment = find(command.orderId());
        CancelRequest request = requestOf(command);
        PaymentCancel repeated = payment.findRepeatedCancel(request).orElse(null);
        if (Objects.nonNull(repeated)) {
            return CancelAttempt.repeat(CancelResults.of(payment, repeated));
        }
        PaymentCancel cancel = payment.requestCancel(request, clock.instant());
        return CancelAttempt.send(payment.getPaymentKey(), cancel.getAmount());
    }

    // 카드사 답(취소 또는 거절)을 남긴다
    @Transactional
    public CancelResult recordOutcome(String orderId, String idempotencyKey, CancelOutcome outcome) {
        Payment payment = find(orderId);
        PaymentCancel cancel = closeWith(payment, idempotencyKey, outcome);
        return CancelResults.of(payment, cancel);
    }

    // 카드사 답을 받지 못했다. 취소됐는지 모른다는 것을 남겨 두고 나중에 카드사에 확인한다
    @Transactional
    public void recordUnknown(String orderId, String idempotencyKey) {
        find(orderId).markCancelUnknown(idempotencyKey);
    }

    // 카드사에 확인한 취소들로 결과를 기다리는 취소를 닫는다
    @Transactional
    public void recordInquiry(String orderId, List<GatewayCancel> gatewayCancels, Instant requestedBefore) {
        find(orderId).settleCancels(gatewayCancels, requestedBefore);
    }

    private static PaymentCancel closeWith(Payment payment, String idempotencyKey, CancelOutcome outcome) {
        if (outcome.isCanceled()) {
            return payment.completeCancel(idempotencyKey, outcome.getCanceled());
        }
        return payment.failCancel(idempotencyKey);
    }

    private Payment find(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    private static CancelRequest requestOf(CancelPaymentCommand command) {
        return CancelRequest.of(command.orderId(), command.idempotencyKey(), command.cancelAmount(), command.cancelReason());
    }
}
