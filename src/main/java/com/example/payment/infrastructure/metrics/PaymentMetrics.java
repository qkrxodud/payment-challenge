package com.example.payment.infrastructure.metrics;

import com.example.payment.domain.outbox.PaymentOutboxRepository;
import com.example.payment.domain.reconciliation.ReconciliationRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 5: 결제 서버의 운영 지표(Micrometer, /actuator/metrics 로 본다). 지표를 읽을 때마다 값을 새로 센다(DB 는 짧은 집계 문장 하나씩).
 * payment.outbox.unsent: 보내지 않은 결제 결과 수 — 릴레이(9장)가 밀린 정도.
 * payment.outbox.oldest-unsent-age: 보내지 않은 결과 가운데 가장 먼저 남긴 것이 기다린 시간(초). 없으면 0.
 * payment.reconciliation.open-mismatches: 운영자가 확인할 대사 어긋남 수(결정 2).
 * payment.pg.circuit.state: 카드사 승인 차단기 상태(0 닫힘, 1 열림, 2 반쯤 열림, 3 꺼짐, 그 밖 4)(결정 4).
 * 실패 모음(11장 DLT) 수는 받는 쪽인 주문 서비스·재고 서비스가 낸다.
 */
@Component
@RequiredArgsConstructor
public class PaymentMetrics implements MeterBinder {

    private static final Map<CircuitBreaker.State, Double> STATES = Map.of(
            CircuitBreaker.State.CLOSED, 0.0,
            CircuitBreaker.State.OPEN, 1.0,
            CircuitBreaker.State.HALF_OPEN, 2.0,
            CircuitBreaker.State.DISABLED, 3.0);
    private static final double OTHER_STATE = 4.0;

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final ReconciliationRepository reconciliationRepository;
    private final CircuitBreaker pgApprovalCircuitBreaker;
    private final Clock clock;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("payment.outbox.unsent", paymentOutboxRepository, PaymentOutboxRepository::countUnsent)
                .description("Payment events not sent to the broker yet")
                .register(registry);
        Gauge.builder("payment.outbox.oldest-unsent-age", this, PaymentMetrics::oldestUnsentAgeSeconds)
                .description("Seconds the oldest unsent payment event has waited")
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder("payment.reconciliation.open-mismatches", reconciliationRepository, ReconciliationRepository::countNeedingReview)
                .description("Reconciliation mismatches left for review")
                .register(registry);
        Gauge.builder("payment.pg.circuit.state", pgApprovalCircuitBreaker, PaymentMetrics::stateOf)
                .description("Card company approval circuit (0 closed, 1 open, 2 half open, 3 disabled, 4 other)")
                .register(registry);
    }

    private double oldestUnsentAgeSeconds() {
        return paymentOutboxRepository.findOldestUnsentOccurredAt()
                .map(oldest -> Duration.between(oldest, clock.instant()).toMillis() / 1000.0)
                .orElse(0.0);
    }

    private static double stateOf(CircuitBreaker circuit) {
        return STATES.getOrDefault(circuit.getState(), OTHER_STATE);
    }
}
