package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.TestPropertySource;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@TestPropertySource(properties = "test.order.components=com.example.payment.application.payment.comparison.NoFilterOrderPaymentService")
@DisplayName("결제 결과 받기(주문 서비스) — 비교(결정 2): 옛 결과를 거르지 않음")
class NoFilterPaymentEventConsumerTest extends PaymentEventConsumerContract {
}
