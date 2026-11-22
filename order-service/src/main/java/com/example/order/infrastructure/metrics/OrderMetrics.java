package com.example.order.infrastructure.metrics;

import com.example.order.domain.deadletter.DeadLetterBacklog;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 5: 주문 서비스의 운영 지표(Micrometer). 지표를 읽을 때마다 값을 새로 센다.
 * order.dead-letter.pending: 실패 모음에 남아 아직 다시 넣지 않은 결제 이벤트 수. 브로커에 묻지 못하면 NaN.
 * 주문 서비스는 웹 서버가 없어 이 지표를 HTTP(/actuator/metrics)로 내보내지 않는다 — 내보내는 길은 보고의 확인 필요에 적었다.
 */
@Component
@RequiredArgsConstructor
public class OrderMetrics implements MeterBinder {

    private final DeadLetterBacklog deadLetterBacklog;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("order.dead-letter.pending", deadLetterBacklog, OrderMetrics::pendingOf)
                .description("Payment events left in the dead letter topic and not replayed yet")
                .register(registry);
    }

    private static double pendingOf(DeadLetterBacklog backlog) {
        return backlog.pending().stream().mapToDouble(pending -> pending).findFirst().orElse(Double.NaN);
    }
}
