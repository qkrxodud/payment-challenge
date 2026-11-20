package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentEvents;
import com.example.payment.application.payment.PaymentHoldings;
import com.example.payment.application.payment.PaymentRecorder;
import com.example.payment.domain.payment.PaymentRepository;
import java.time.Clock;
import org.springframework.context.annotation.Primary;

/**
 * 비교 구현(10장 결정 4, 실패로만 봄): 재고 서비스의 답을 받지 못하면 재고가 모자란 것과 같이 결제를 승인 없이 닫고 쿠폰·포인트만 돌려준다 —
 * 해제를 보내지 않는다(결정 1~3 의 운영). 재고 서비스가 실제로 잡았거나 요청이 늦게 도착하면 그 재고는 돌아오지 않는다.
 */
@Primary
public class NoReleaseOnTimeoutRecorder extends PaymentRecorder {

    public NoReleaseOnTimeoutRecorder(PaymentRepository paymentRepository, PaymentHoldings paymentHoldings,
                                      PaymentEvents paymentEvents, Clock clock) {
        super(paymentRepository, paymentHoldings, paymentEvents, clock);
    }

    @Override
    public void recordStockNoResponse(String orderId) {
        recordStockRefused(orderId);
    }
}
