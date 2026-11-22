package com.example.payment.infrastructure.pg;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 12장 시험 도구: 열리지 않는 차단기. 차단기와 상관없는 비교 구현(5장 취소)과 12장 결정 4 비교(시간 제한만, 다시 시도)가 쓴다 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DisabledCircuit {

    public static CircuitBreaker create() {
        CircuitBreaker circuit = CircuitBreaker.ofDefaults("disabled-" + System.nanoTime());
        circuit.transitionToDisabledState();
        return circuit;
    }
}
