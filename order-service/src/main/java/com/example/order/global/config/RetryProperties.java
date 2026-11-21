package com.example.order.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 11장 결정 3: 결제 이벤트 처리가 일시 오류로 실패했을 때 같은 자리에서 다시 시도하는 간격과 횟수.
 * 첫 간격 initialInterval 에서 multiplier 배씩 늘리되 maxInterval 을 넘지 않고, maxRetries 번 다시 시도한다(처음 시도는 세지 않는다).
 * 값을 정한 근거는 order-service.properties 와 docs/ch11/environment.md.
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "order.retry")
public class RetryProperties {

    private final Duration initialInterval;
    private final double multiplier;
    private final Duration maxInterval;
    private final int maxRetries;
}
