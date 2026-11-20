package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.InquiringHoldings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(InquiringHoldings.class)
@DisplayName("결제와 나눈 재고를 함께 끝내기 — 비교(결정 4): 재고 서비스 답이 없으면 예약을 조회해 확인한 뒤 진행")
class InquiringSagaTest extends SagaContract {

    @Override
    Class<?>[] otherServerComponents() {
        return new Class<?>[]{InquiringHoldings.class};
    }
}
