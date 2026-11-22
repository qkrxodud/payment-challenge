package com.example.payment.application.payment;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 5: 매일 정한 때(payment.outbox.cleanup.cron)에 보낸 결제 결과를 정리한다.
 * 시험은 정리를 직접 부르므로 payment.outbox.cleanup.enabled=false 로 끈다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.outbox.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxCleanupScheduler {

    private final OutboxCleanup outboxCleanup;
    private final Clock clock;

    @Scheduled(cron = "${payment.outbox.cleanup.cron}")
    public void cleanUp() {
        outboxCleanup.cleanUp(clock.instant());
    }
}
