package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.dto.CancelAttempt;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.payment.CancelOutcome;
import com.example.payment.domain.payment.CancelRequest;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentCancel;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import java.time.Clock;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 비교 구현 C(5장 결정 4): 카드사 먼저 부르고 기록. 같은 키 확인과 받을 수 있는 취소인지 확인하는 규칙은 운영 구현과 같지만
 * 카드사에 보내기 전에는 아무것도 남기지 않고, 카드사가 답한 뒤에야 취소 한 건을 남긴다. 카드사 답을 받지 못하면 남는 것이 없다.
 * 카드사 호출은 트랜잭션 밖에서 하고, 카드사에 멱등키를 넘기는 것(결정 5)은 운영 구현과 같다.
 */
@RequiredArgsConstructor
public class GatewayFirstCancelService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CancelResult cancel(CancelPaymentCommand command) {
        CancelAttempt attempt = transactionTemplate.execute(status -> check(command, status));
        if (attempt.isRepeated()) {
            return attempt.repeated();
        }
        CancelOutcome outcome = paymentGateway.cancel(attempt.paymentKey(), command.orderId(), command.idempotencyKey(),
                attempt.amount(), command.cancelReason());
        return transactionTemplate.execute(status -> record(command, outcome));
    }

    // 운영 구현과 같은 확인을 하되 남기지 않는다: 확인에 쓴 요청 기록은 트랜잭션을 되돌려 버린다
    private CancelAttempt check(CancelPaymentCommand command, TransactionStatus status) {
        Payment payment = find(command.orderId());
        CancelRequest request = requestOf(command);
        PaymentCancel repeated = payment.findRepeatedCancel(request).orElse(null);
        status.setRollbackOnly();
        if (Objects.nonNull(repeated)) {
            return CancelAttempt.repeat(resultOf(payment, repeated));
        }
        return CancelAttempt.send(payment.getPaymentKey(), payment.requestCancel(request, clock.instant()).getAmount());
    }

    private CancelResult record(CancelPaymentCommand command, CancelOutcome outcome) {
        Payment payment = find(command.orderId());
        payment.requestCancel(requestOf(command), clock.instant());
        PaymentCancel cancel = closeWith(payment, command.idempotencyKey(), outcome);
        return resultOf(payment, cancel);
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

    private static CancelResult resultOf(Payment payment, PaymentCancel cancel) {
        return new CancelResult(payment.getOrderId(), cancel.getAmount(), cancel.getStatus(), cancel.getBalanceAfter());
    }
}
