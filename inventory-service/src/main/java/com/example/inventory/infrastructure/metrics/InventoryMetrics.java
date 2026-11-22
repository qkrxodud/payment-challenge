package com.example.inventory.infrastructure.metrics;

import com.example.inventory.domain.deadletter.DeadLetterBacklog;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 5: 재고 서비스의 운영 지표(Micrometer). 지표를 읽을 때마다 값을 새로 센다. HTTP 로는 /actuator/metrics 에서 본다.
 * inventory.dead-letter.pending: 실패 모음에 남아 아직 다시 넣지 않은 재고 명령 수. 브로커에 묻지 못하면 NaN.
 */
@Component
@RequiredArgsConstructor
public class InventoryMetrics implements MeterBinder {

    private final DeadLetterBacklog deadLetterBacklog;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("inventory.dead-letter.pending", deadLetterBacklog, InventoryMetrics::pendingOf)
                .description("Stock commands left in the dead letter topic and not replayed yet")
                .register(registry);
    }

    private static double pendingOf(DeadLetterBacklog backlog) {
        return backlog.pending().stream().mapToDouble(pending -> pending).findFirst().orElse(Double.NaN);
    }
}
