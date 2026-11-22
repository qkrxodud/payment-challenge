package com.example.payment.application.payment;

import com.example.payment.domain.outbox.PaymentOutboxRepository;
import com.example.payment.global.config.OutboxCleanupProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 5: 보존 기간이 지난 보낸 결제 결과를 지운다. 브로커가 받았다고 답한 기록만 지운다 — 보내지 않은 기록은 릴레이가 보낼 때까지 남는다.
 * 지워도 이벤트 번호는 다시 쓰이지 않는다(MySQL 8.4 는 auto_increment 의 가장 큰 값을 다시 시작해도 기억한다 — docs/ch12/environment.md) —
 * 받는 쪽 11장 결정 1·2가 번호로 가리는 것이 깨지지 않는다. 한 번에 정한 줄 수만큼 지우는 짧은 문장을 되풀이한다.
 * 서버 여러 대 가운데 작업 잠금을 맡은 한 대만 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanup {

    private final ExclusiveJobRunner exclusiveJobRunner;
    private final PaymentOutboxRepository paymentOutboxRepository;
    private final OutboxCleanupProperties outboxCleanupProperties;

    public void cleanUp(Instant now) {
        exclusiveJobRunner.run(ExclusiveJobRunner.OUTBOX_CLEANUP, () -> cleanUpLocked(now));
    }

    private void cleanUpLocked(Instant now) {
        Instant cutoff = now.minus(outboxCleanupProperties.getRetention());
        int deleted = 0;
        int round;
        do {
            round = paymentOutboxRepository.deleteSentBefore(cutoff, outboxCleanupProperties.getBatchSize());
            deleted += round;
        } while (round >= outboxCleanupProperties.getBatchSize());
        log.info("Deleted {} payment events sent before {}.", deleted, cutoff);
    }
}
