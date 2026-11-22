package com.example.payment.infrastructure.pg;

import com.example.payment.domain.error.exception.PaymentGatewayFailedException;
import com.example.payment.domain.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.global.config.PgCircuitBreakerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 12장 결정 4: 카드사 승인 요청 차단기. 답이 없거나(시간 초과·연결 실패) 카드사 오류(5xx)인 승인만 실패로 센다 — 카드사가 거절한 승인(4xx)은
 * 카드사가 정상으로 답한 것이라 성공으로 센다(Resilience4j: recordExceptions 에 없는 예외와 정상 반환은 성공).
 * Spring Boot 연동 모듈(resilience4j-spring-boot4)을 쓰지 않고 이 빈 하나만 만든다 — 쓰는 곳이 승인 하나라 설정 바인딩·애너테이션이 필요 없다.
 */
@Configuration
@EnableConfigurationProperties(PgCircuitBreakerProperties.class)
public class PgCircuitBreakerConfig {

    public static final String APPROVAL = "pg-approval";

    @Bean
    CircuitBreaker pgApprovalCircuitBreaker(PgCircuitBreakerProperties properties) {
        CircuitBreaker circuit = circuitOf(properties);
        if (properties.isEnabled()) {
            return circuit;
        }
        circuit.transitionToDisabledState();
        return circuit;
    }

    private static CircuitBreaker circuitOf(PgCircuitBreakerProperties properties) {
        return CircuitBreaker.of(APPROVAL, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .failureRateThreshold(properties.getFailureRateThreshold())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedCallsInHalfOpenState())
                .recordExceptions(PaymentGatewayNoResponseException.class, PaymentGatewayFailedException.class)
                .build());
    }
}
