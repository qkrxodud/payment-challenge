package com.example.payment.application.payment;

import com.example.payment.infrastructure.pg.RetryingPaymentGateway;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(RetryingPaymentGateway.class)
@DisplayName("카드사 장애 동안의 승인 — 비교(결정 4): 다시 시도")
class RetryingPgOutageTest extends PgOutageContract {

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
