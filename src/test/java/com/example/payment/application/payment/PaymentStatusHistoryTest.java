package com.example.payment.application.payment;

import java.util.List;
import org.junit.jupiter.api.DisplayName;

@DisplayName("결제 상태 이력 — 운영 구현")
class PaymentStatusHistoryTest extends StatusHistoryContract {

    // 12장 출발 구조: 상태가 바뀐 이력을 남기는 곳이 없다(결제 줄에는 지금 상태만 있다)
    @Override
    List<HistoryLine> historyOf(String orderId) {
        return List.of();
    }
}
