package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentCancelService;
import com.example.payment.application.payment.PaymentConfirmService;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.error.PaymentChallengeException;
import com.example.payment.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;

/**
 * 비교 구현(8장 결정 2): 카드사가 승인한 뒤에 재고·쿠폰·포인트를 잡는다. 모자라면 방금 받은 승인을 카드사에 전액 취소하고(카드사를 한 번 더 부름)
 * 잡지 못한 이유로 답한다. 고객 카드에는 승인과 취소가 함께 남는다.
 */
@RequiredArgsConstructor
public class HoldAfterApprovalConfirmService {

    private final PaymentConfirmService paymentConfirmService;
    private final PaymentCancelService paymentCancelService;
    private final DeferredHoldRecorder deferredHoldRecorder;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        PaymentResult result = paymentConfirmService.confirm(command);
        if (result.status() != PaymentStatus.APPROVED) {
            return result;
        }
        try {
            deferredHoldRecorder.hold(command.orderId());
            return result;
        } catch (PaymentChallengeException notHeld) {
            paymentCancelService.cancel(new CancelPaymentCommand(command.orderId(), "not-held-" + command.orderId(), null,
                    "Could not hold the order"));
            throw notHeld;
        }
    }
}
