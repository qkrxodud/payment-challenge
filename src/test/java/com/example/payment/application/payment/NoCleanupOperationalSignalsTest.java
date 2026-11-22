package com.example.payment.application.payment;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("운영 지표와 정리 — 비교(결정 5): 지우지 않음")
class NoCleanupOperationalSignalsTest extends PaymentOperationalSignalsTest {

    // 11장까지처럼 보낸 결과와 받은 기록을 지우지 않는다(지표는 운영과 같다)
    @Override
    void cleanUpPayments(Instant now) {
    }

    @Override
    void cleanUpOrders(Instant now) {
    }

    @Override
    void cleanUpInventory(Instant now) {
    }
}
