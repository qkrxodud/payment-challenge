package com.example.order.application.cleanup;

import com.example.order.domain.order.ReceivedPaymentEventRepository;
import com.example.order.global.config.CleanupProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 12장 결정 5: 보존 기간이 지난 받은 결제 이벤트 기록을 지운다. 한 번에 정한 줄 수만큼 지우는 짧은 문장을 되풀이한다 — 한 문장으로 오래 잠그지 않는다.
 * 같은 이벤트가 다시 와도(결제 서버가 다시 보냄, 실패 모음에서 다시 넣음) 주문은 11장 결정 2(마지막으로 반영한 번호)로 다시 바뀌지 않는다.
 * 기록이 지워진 뒤 다시 온 이벤트는 받은 기록에 한 줄 다시 남는다(결정 1이 가리던 것을 결정 2가 가린다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceivedEventCleanup {

    private final ReceivedPaymentEventRepository receivedPaymentEventRepository;
    private final CleanupProperties cleanupProperties;

    public int cleanUp(Instant now) {
        Instant cutoff = now.minus(cleanupProperties.getRetention());
        int deleted = 0;
        int round;
        do {
            round = receivedPaymentEventRepository.deleteReceivedBefore(cutoff, cleanupProperties.getBatchSize());
            deleted += round;
        } while (round >= cleanupProperties.getBatchSize());
        log.info("Deleted {} received payment events older than {}.", deleted, cutoff);
        return deleted;
    }
}
