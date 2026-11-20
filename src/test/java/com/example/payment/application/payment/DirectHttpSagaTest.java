package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.DirectHttpHoldings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(DirectHttpHoldings.class)
@TestPropertySource(properties = "test.inventory.components=com.example.inventorycomparison.ReservationCommandController")
@DisplayName("결제와 나눈 재고를 함께 끝내기 — 비교(결정 5): 상태를 바꾼 자리에서 바로 HTTP 로 확정·해제")
class DirectHttpSagaTest extends SagaContract {

    @Override
    Class<?>[] otherServerComponents() {
        return new Class<?>[]{DirectHttpHoldings.class};
    }
}
