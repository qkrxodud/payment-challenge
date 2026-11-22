package com.example.inventory.application.cleanup;

import com.example.inventory.domain.command.ProcessedStockCommandRepository;
import com.example.inventory.global.config.CleanupProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 12장 결정 5: 보존 기간이 지난 처리한 재고 명령 기록을 지운다. 한 번에 정한 줄 수만큼 지우는 짧은 문장을 되풀이한다.
 * 같은 명령이 다시 와도 예약은 11장 결정 2(마지막으로 반영한 명령 번호)와 10장 예약 상태 전이로 다시 바뀌지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessedCommandCleanup {

    private final ProcessedStockCommandRepository processedStockCommandRepository;
    private final CleanupProperties cleanupProperties;

    public int cleanUp(Instant now) {
        Instant cutoff = now.minus(cleanupProperties.getRetention());
        int deleted = 0;
        int round;
        do {
            round = processedStockCommandRepository.deleteProcessedBefore(cutoff, cleanupProperties.getBatchSize());
            deleted += round;
        } while (round >= cleanupProperties.getBatchSize());
        log.info("Deleted {} processed stock commands older than {}.", deleted, cutoff);
        return deleted;
    }
}
