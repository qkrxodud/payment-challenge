package com.example.payment.global.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 12장 결정 4: 카드사 승인 요청 차단기 설정(Resilience4j CircuitBreaker, 횟수로 보는 창).
 * failureRateThreshold: 최근 slidingWindowSize 번의 승인 가운데 실패(답 없음·카드사 오류)가 이 비율(%) 이상이면 연다.
 * minimumNumberOfCalls: 이만큼 부르기 전에는 실패율을 계산하지 않는다 — 열리기 전까지 결과 모름이 될 수 있는 승인 요청 수의 위쪽 끝이다.
 * waitDurationInOpenState: 연 뒤 이만큼 승인을 요청하지 않는다. permittedCallsInHalfOpenState: 그 뒤 시험 삼아 보내는 승인 수.
 * enabled: 끄면 차단기가 열리지 않는다(시험은 끄고, 차단기 계약·측정만 켠다 — 앞 장 시험이 카드사 장애를 여러 번 만들어 차단기가 열리면 뒤 시험이 막힌다).
 */
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "payment.pg.circuit-breaker")
public class PgCircuitBreakerProperties {

    private final float failureRateThreshold;
    private final int slidingWindowSize;
    private final int minimumNumberOfCalls;
    private final Duration waitDurationInOpenState;
    private final int permittedCallsInHalfOpenState;
    private final boolean enabled;
}
