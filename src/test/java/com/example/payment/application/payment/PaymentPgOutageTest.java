package com.example.payment.application.payment;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;

@DisplayName("카드사 장애 동안의 승인 — 운영 구현")
class PaymentPgOutageTest extends PgOutageContract {

    // 12장 출발 구조: 승인 요청은 2장의 시간 제한만 있다. 지난 실패를 기억하는 곳이 없다
    @Override
    void forgetFailures() {
    }

    // 12장 출발 구조: 운영 지표가 없다
    @Override
    Optional<Double> circuitStateMeter() {
        return Optional.empty();
    }
}
