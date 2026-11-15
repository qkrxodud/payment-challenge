package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentCancelService;
import com.example.payment.application.payment.PaymentQueryService;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.payment.CancelStatus;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * 비교 구현 B(5장 결정 3): 지금 상태로 답함(3장 승인 재요청 방식). 요청을 기록해 두지 않고,
 * 결제가 이미 취소 상태면 다시 온 요청으로 보고 지금 상태로 답한다. 아니면 새 취소로 운영 구현을 부른다(키는 요청마다 새로 만든다).
 */
@RequiredArgsConstructor
public class CurrentStateCancelService {

    private static final Set<PaymentStatus> CANCELED = Set.of(PaymentStatus.APPROVAL_CANCELED, PaymentStatus.CAPTURE_CANCELED);

    private final PaymentCancelService paymentCancelService;
    private final PaymentQueryService paymentQueryService;

    public CancelResult cancel(CancelPaymentCommand command) {
        if (CANCELED.contains(paymentQueryService.find(command.orderId()).status())) {
            return new CancelResult(command.orderId(), command.cancelAmount(), CancelStatus.DONE, BigDecimal.ZERO);
        }
        return paymentCancelService.cancel(new CancelPaymentCommand(
                command.orderId(), "state-" + UUID.randomUUID(), command.cancelAmount(), command.cancelReason()));
    }
}
