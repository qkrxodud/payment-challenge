package com.example.payment.application.payment.comparison;

import com.example.payment.domain.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/** 비교 구현(8장 결정 2): 결제 줄을 잠그고 그 결제의 재고·쿠폰·포인트를 한 트랜잭션에서 잡는다. 모자라면 아무것도 잡지 않는다 */
@RequiredArgsConstructor
public class DeferredHoldRecorder {

    private final PaymentRepository paymentRepository;
    private final DeferredHoldings deferredHoldings;

    @Transactional
    public void hold(String orderId) {
        paymentRepository.findByOrderIdForUpdate(orderId).ifPresent(deferredHoldings::holdNow);
    }
}
