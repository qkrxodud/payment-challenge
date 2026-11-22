package com.example.inventory.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 12장 결정 5: 처리한 재고 명령 기록의 정리. retention 보다 오래전에 처리한 기록을 batchSize 줄씩 지운다.
 * 지워도 같은 명령이 다시 오면 11장 결정 2(예약마다 마지막으로 반영한 번호)와 10장 예약 상태 전이가 예약을 바꾸지 않는다 — 근거는 inventory-service.properties
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "inventory.cleanup")
public class CleanupProperties {

    private final Duration retention;
    private final int batchSize;
}
