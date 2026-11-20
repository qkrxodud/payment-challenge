package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelAttempt;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.error.exception.PaymentNotFoundException;
import com.example.payment.domain.payment.CancelOutcome;
import com.example.payment.domain.payment.CancelRequest;
import com.example.payment.domain.payment.GatewayCancel;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentCancel;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 취소 앞뒤의 기록을 각각 짧은 트랜잭션으로 남긴다(5장). PaymentRecorder 와 같은 이유로 취소 서비스와 다른 빈으로 둔다.
 * 7장부터 결제 줄을 잠그고 읽는다(결정 1). 더 취소할 수 있는 금액은 취소 줄들의 합으로 계산하므로, 같은 결제에 동시에 온 취소는
 * 앞 취소가 요청을 남길 때까지 기다렸다가 그 요청까지 빼고 계산한다. 같은 Idempotency-Key 로 동시에 온 취소도 기다린 뒤 앞 취소를
 * 찾아 5장 규칙(같은 키로 다시 온 요청)대로 답한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentCancelRecorder {

    private final PaymentRepository paymentRepository;
    private final PaymentHoldings paymentHoldings;
    private final PaymentEvents paymentEvents;
    private final Clock clock;

    // 같은 Idempotency-Key 로 이미 받은 취소가 결과를 남겼으면 그 결과를 돌려준다.
    // 아직 카드사 답을 남기지 못한 취소면 같은 키로 카드사에 다시 보낸다(5장 결정 5 — 카드사도 같은 키에는 다시 취소하지 않는다).
    // 새 취소면 받을 수 있는지 보고 카드사 답을 기다리는 취소로 남긴다
    @Transactional
    public CancelAttempt request(CancelPaymentCommand command) {
        Payment payment = find(command.orderId());
        CancelRequest request = requestOf(command);
        PaymentCancel repeated = payment.findRepeatedCancel(request).orElse(null);
        if (Objects.isNull(repeated)) {
            PaymentCancel cancel = payment.requestCancel(request, clock.instant());
            return CancelAttempt.send(payment.getPaymentKey(), cancel.getAmount());
        }
        if (repeated.isAwaitingResult()) {
            return CancelAttempt.send(payment.getPaymentKey(), repeated.getAmount());
        }
        return CancelAttempt.repeat(CancelResults.of(payment, repeated));
    }

    // 카드사 답(취소 또는 거절)을 남긴다. 카드사가 같은 키의 앞 요청을 아직 처리 중이면 그대로 둔다.
    // 8장 결정 4부터 이 취소로 남은 금액이 0원이 돼 결제가 취소 상태가 되면 잡아 둔 재고·포인트·쿠폰을 돌려준다(부분 취소는 카드 금액만).
    // 9장부터 결제가 취소 상태가 되면 주문 서비스에 알린다(부분 취소는 결제 상태가 그대로라 알리지 않는다)
    @Transactional
    public CancelResult recordOutcome(String orderId, String idempotencyKey, CancelOutcome outcome) {
        Payment payment = find(orderId);
        PaymentStatus before = payment.getStatus();
        PaymentCancel cancel = closeWith(payment, idempotencyKey, outcome);
        paymentHoldings.settle(payment, before);
        paymentEvents.publishIfChanged(payment, before);
        return CancelResults.of(payment, cancel);
    }

    // 카드사 답을 받지 못했다. 취소됐는지 모른다는 것을 남겨 두고 나중에 카드사에 확인한다
    @Transactional
    public void recordUnknown(String orderId, String idempotencyKey) {
        find(orderId).markCancelUnknown(idempotencyKey);
    }

    // 카드사에 확인한 취소들로 결과를 기다리는 취소를 닫는다
    // 8장 결정 4부터 이 보정으로 결제가 취소 상태가 되면 잡아 둔 것을 돌려준다. 9장부터 주문 서비스에도 알린다
    @Transactional
    public void recordInquiry(String orderId, List<GatewayCancel> gatewayCancels, Instant requestedBefore) {
        Payment payment = find(orderId);
        PaymentStatus before = payment.getStatus();
        payment.settleCancels(gatewayCancels, requestedBefore);
        paymentHoldings.settle(payment, before);
        paymentEvents.publishIfChanged(payment, before);
    }

    private static PaymentCancel closeWith(Payment payment, String idempotencyKey, CancelOutcome outcome) {
        if (outcome.isInProgress()) {
            return payment.findCancel(idempotencyKey).orElseThrow();
        }
        if (outcome.isCanceled()) {
            return payment.completeCancel(idempotencyKey, outcome.getCanceled());
        }
        return payment.failCancel(idempotencyKey);
    }

    private Payment find(String orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    private static CancelRequest requestOf(CancelPaymentCommand command) {
        return CancelRequest.of(command.orderId(), command.idempotencyKey(), command.cancelAmount(), command.cancelReason());
    }
}
