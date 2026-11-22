package com.example.payment.application.payment;

import org.springframework.test.context.TestPropertySource;

/** 12장 측정: 운영 구현(결정 4 차단기, 운영 값). 시험은 차단기를 기본으로 끄므로 이 측정에서 켠다 */
@TestPropertySource(properties = "payment.pg.circuit-breaker.enabled=true")
class CircuitBreakerPgOutageMeasurement extends PgOutageMeasurement {

    @Override
    String method() {
        return "운영 — 차단기";
    }
}
