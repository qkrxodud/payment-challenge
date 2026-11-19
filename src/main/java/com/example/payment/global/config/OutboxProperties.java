package com.example.payment.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 9장: 남긴 결제 결과를 브로커에 보내는 릴레이 설정(결정 2).
 * interval: 한 주기를 마친 뒤 다음 주기까지 쉬는 시간 — 남긴 결과는 길게는 이만큼 늦게 나간다.
 * batchSize: 한 주기에 읽는 아직 보내지 않은 결과의 수(번호 순).
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "payment.outbox")
public class OutboxProperties {

    private final Duration interval;
    private final int batchSize;
}
