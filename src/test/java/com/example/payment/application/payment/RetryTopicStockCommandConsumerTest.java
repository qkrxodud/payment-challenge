package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.TestPropertySource;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@TestPropertySource(properties = "test.inventory.components=com.example.consumercomparison.RetryTopicStockConsumer")
@DisplayName("재고 명령 받기(재고 서비스) — 비교(결정 3): 다시 시도 토픽(non-blocking)")
class RetryTopicStockCommandConsumerTest extends StockCommandConsumerContract {
}
