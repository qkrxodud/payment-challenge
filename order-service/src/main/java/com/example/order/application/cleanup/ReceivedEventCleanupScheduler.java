package com.example.order.application.cleanup;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 5: 매일 정한 때(order.cleanup.cron)에 받은 기록을 정리한다. 서버가 여러 대면 저마다 돌지만 같은 줄을 지우는 일이라 결과가 같다.
 * 시험은 정리를 직접 부르므로 order.cleanup.enabled=false 로 끈다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReceivedEventCleanupScheduler {

    private final ReceivedEventCleanup receivedEventCleanup;
    private final Clock clock;

    @Scheduled(cron = "${order.cleanup.cron}")
    public void cleanUp() {
        receivedEventCleanup.cleanUp(clock.instant());
    }
}
