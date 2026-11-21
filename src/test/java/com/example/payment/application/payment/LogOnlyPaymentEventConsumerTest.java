package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.TestPropertySource;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@TestPropertySource(properties = "test.order.components=com.example.consumercomparison.LogOnlyErrorHandling")
@DisplayName("결제 결과 받기(주문 서비스) — 비교(결정 4): 다시 시도를 다 쓰면 로그만 남김")
class LogOnlyPaymentEventConsumerTest extends PaymentEventConsumerContract {
}
