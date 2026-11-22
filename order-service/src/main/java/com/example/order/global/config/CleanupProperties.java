package com.example.order.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 12장 결정 5: 받은 결제 이벤트 기록의 정리. retention 보다 오래전에 받은 기록을 batchSize 줄씩 지운다.
 * 지워도 같은 이벤트가 다시 오면 11장 결정 2(주문마다 마지막으로 반영한 번호)가 주문을 바꾸지 않는다 — 근거는 order-service.properties
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "order.cleanup")
public class CleanupProperties {

    private final Duration retention;
    private final int batchSize;
}
