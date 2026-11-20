package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.ChoreographyHoldings;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 결정 2 비교: 같은 계약으로 돌린다. 결제 서버의 결제 이벤트 토픽은 이 시험 컨텍스트만의 이름으로 바꾼다
 * (재고 서비스가 다른 컨텍스트의 결제 이벤트 — 결제 번호가 겹친다 — 를 읽지 않게).
 */
@Import(ChoreographyHoldings.class)
@TestPropertySource(properties = {
        "test.inventory.reads-payment-events=true",
        "test.inventory.components=com.example.payment.application.payment.comparison.PaymentEventReactionListener"
})
@DisplayName("결제와 나눈 재고를 함께 끝내기 — 비교(결정 2): 코레오그래피, 재고 서비스가 결제 이벤트를 보고 스스로 확정·해제")
class ChoreographySagaTest extends SagaContract {

    @Override
    Class<?>[] otherServerComponents() {
        return new Class<?>[]{ChoreographyHoldings.class};
    }
}
