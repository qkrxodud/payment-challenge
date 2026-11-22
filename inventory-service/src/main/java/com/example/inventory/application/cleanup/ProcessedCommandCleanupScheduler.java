package com.example.inventory.application.cleanup;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 5: 매일 정한 때(inventory.cleanup.cron)에 처리한 명령 기록을 정리한다. 서버가 여러 대면 저마다 돌지만 같은 줄을 지우는 일이라 결과가 같다.
 * 시험은 정리를 직접 부르므로 inventory.cleanup.enabled=false 로 끈다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "inventory.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProcessedCommandCleanupScheduler {

    private final ProcessedCommandCleanup processedCommandCleanup;
    private final Clock clock;

    @Scheduled(cron = "${inventory.cleanup.cron}")
    public void cleanUp() {
        processedCommandCleanup.cleanUp(clock.instant());
    }
}
