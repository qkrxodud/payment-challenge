package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.TestPropertySource;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@TestPropertySource(properties = "test.order.components=com.example.consumercomparison.SpringDefaultErrorHandling")
@DisplayName("결제 결과 받기(주문 서비스) — 비교(결정 3): Spring Kafka 기본 처리(간격 없이 10번, 로그)")
class DefaultHandlingPaymentEventConsumerTest extends PaymentEventConsumerContract {
}
