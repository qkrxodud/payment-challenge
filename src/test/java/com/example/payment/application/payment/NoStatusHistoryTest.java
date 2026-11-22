package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NoStatusHistory;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(NoStatusHistory.class)
@DisplayName("결제 상태 이력 — 비교(결정 3): 이력 없음")
class NoStatusHistoryTest extends StatusHistoryContract {

    // 운영 구현과 같은 상태 이력 표를 읽는다(이 비교는 아무것도 남기지 않는다)
    @Override
    List<HistoryLine> historyOf(String orderId) {
        return PaymentStatusHistoryTest.historyIn(jdbcTemplate, orderId);
    }
}
