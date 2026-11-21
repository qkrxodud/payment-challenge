package com.example.inventory.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 11장 결정 3: 재고 명령 처리가 일시 오류로 실패했을 때 같은 자리에서 다시 시도하는 간격과 횟수(주문 서비스와 같은 규칙).
 * 첫 간격 initialInterval 에서 multiplier 배씩 늘리되 maxInterval 을 넘지 않고, maxRetries 번 다시 시도한다(처음 시도는 세지 않는다).
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "inventory.retry")
public class RetryProperties {

    private final Duration initialInterval;
    private final double multiplier;
    private final Duration maxInterval;
    private final int maxRetries;
}
