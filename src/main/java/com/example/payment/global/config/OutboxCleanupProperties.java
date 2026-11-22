package com.example.payment.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 12장 결정 5: 보낸 결제 결과(payment_outbox)의 정리. retention 보다 오래전에 보낸 기록을 batchSize 줄씩 지운다. 보내지 않은 기록은 지우지 않는다
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "payment.outbox.cleanup")
public class OutboxCleanupProperties {

    private final Duration retention;
    private final int batchSize;
}
