package com.example.payment.application.payment;

import com.example.payment.infrastructure.pg.TimeoutOnlyPaymentGateway;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(TimeoutOnlyPaymentGateway.class)
@DisplayName("카드사 장애 동안의 승인 — 비교(결정 4): 시간 제한만")
class TimeoutOnlyPgOutageTest extends PgOutageContract {

    // 지난 실패를 기억하지 않는다
    @Override
    void forgetFailures() {
    }

    // 차단기가 없다
    @Override
    Optional<Double> circuitStateMeter() {
        return Optional.empty();
    }
}
