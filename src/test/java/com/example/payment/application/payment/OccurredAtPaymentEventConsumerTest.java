package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.TestPropertySource;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@TestPropertySource(properties = "test.order.components=com.example.payment.application.payment.comparison.OccurredAtOrderPaymentService")
@DisplayName("결제 결과 받기(주문 서비스) — 비교(결정 2): 만든 시각으로 비교")
class OccurredAtPaymentEventConsumerTest extends PaymentEventConsumerContract {
}
