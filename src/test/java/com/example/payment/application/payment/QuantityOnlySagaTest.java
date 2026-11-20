package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.QuantityStockReservations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(QuantityStockReservations.class)
@TestPropertySource(properties = "test.inventory.components="
        + "com.example.inventorycomparison.QuantityStockController,"
        + "com.example.payment.application.payment.comparison.QuantityStockRecorder")
@DisplayName("결제와 나눈 재고를 함께 끝내기 — 비교(결정 3): 예약 기록 없이 수량만 바로 줄이고 늘림")
class QuantityOnlySagaTest extends SagaContract {

    @Override
    Class<?>[] otherServerComponents() {
        return new Class<?>[]{QuantityStockReservations.class};
    }
}
