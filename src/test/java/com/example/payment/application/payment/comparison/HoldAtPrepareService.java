package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentPrepareService;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import lombok.RequiredArgsConstructor;

/**
 * 비교 구현(8장 결정 2): 준비할 때 재고·쿠폰·포인트를 잡는다. 모자라면 준비를 받지 않는다(결제는 준비 상태로 남지만 아무것도 잡지 않음).
 * 승인을 요청하지 않는 결제도 준비한 때부터 재고를 잡고 있다.
 */
@RequiredArgsConstructor
public class HoldAtPrepareService {

    private final PaymentPrepareService paymentPrepareService;
    private final DeferredHoldRecorder deferredHoldRecorder;

    public PaymentResult prepare(PreparePaymentCommand command) {
        PaymentResult prepared = paymentPrepareService.prepare(command);
        deferredHoldRecorder.hold(command.orderId());
        return prepared;
    }
}
