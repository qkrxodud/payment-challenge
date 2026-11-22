package com.example.payment.application.payment;

import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatusHistory;
import com.example.payment.domain.payment.PaymentStatusHistoryRepository;
import com.example.payment.domain.payment.StatusTrigger;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 3: 결제 상태 이력을 남긴다. 결제를 바꾸는 기록(PaymentRecorder 등)이 결제 상태를 바꾼 바로 뒤, 같은 트랜잭션 안에서 부른다 —
 * 상태가 커밋되면 이력도 반드시 남고, 커밋이 실패하면 함께 남지 않는다(9장 결제 결과 기록과 같은 자리).
 * 결제가 이번에 거친 전이를 모두 한 줄씩 남긴다(보정이 결과 모름을 승인으로 닫고 바로 카드사 취소까지 들여오면 두 줄). 바뀐 것이 없으면 남기지 않는다.
 * 9장 결제 결과 기록(payment_outbox)으로 대신하지 않는 이유: 그것은 주문 서비스에 알릴 결과 상태만 싣고(인증·결과 모름은 없음),
 * 이전 상태·행위·계기가 없으며, 보낸 뒤에는 지운다(결정 5).
 */
@Component
@RequiredArgsConstructor
public class PaymentHistories {

    private final PaymentStatusHistoryRepository paymentStatusHistoryRepository;
    private final Clock clock;

    public void record(Payment payment, StatusTrigger trigger) {
        Instant now = clock.instant();
        paymentStatusHistoryRepository.saveAll(payment.drainTransitions().stream()
                .map(transition -> PaymentStatusHistory.of(payment.getId(), transition, trigger, now))
                .toList());
    }
}
