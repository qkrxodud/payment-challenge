package com.example.payment.application.payment;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("카드사 장애 동안의 승인 — 운영 구현")
class PaymentPgOutageTest extends PgOutageContract {

    @Autowired
    CircuitBreaker pgApprovalCircuitBreaker;

    @Override
    void forgetFailures() {
        pgApprovalCircuitBreaker.reset();
    }

    // 운영 지표는 결정 5에서 더한다
    @Override
    Optional<Double> circuitStateMeter() {
        return Optional.empty();
    }
}
