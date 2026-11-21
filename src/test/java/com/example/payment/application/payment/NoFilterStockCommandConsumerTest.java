package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.TestPropertySource;

/** 결정 2 비교: 재고 서비스에 옛 명령 거르기가 더 필요한지 같은 계약으로 본다. 태그는 실행 결과를 본 뒤 단다 */
@TestPropertySource(properties = "test.inventory.components=com.example.payment.application.payment.comparison.NoFilterReservationRecorder")
@DisplayName("재고 명령 받기(재고 서비스) — 비교(결정 2): 옛 명령을 거르지 않음, 10장 상태 전이만")
class NoFilterStockCommandConsumerTest extends StockCommandConsumerContract {
}
