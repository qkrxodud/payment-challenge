package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentHistories;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatusHistoryRepository;
import com.example.payment.domain.payment.StatusTrigger;
import java.time.Clock;
import org.springframework.context.annotation.Primary;

/**
 * 12장 결정 3 비교: 이력 없음(11장까지). 결제 줄에는 지금 상태만 남고, 상태가 바뀐 이력은 남기지 않는다
 */
@Primary
public class NoStatusHistory extends PaymentHistories {

    public NoStatusHistory(PaymentStatusHistoryRepository paymentStatusHistoryRepository, Clock clock) {
        super(paymentStatusHistoryRepository, clock);
    }

    @Override
    public void record(Payment payment, StatusTrigger trigger) {
        payment.drainTransitions();
    }
}
