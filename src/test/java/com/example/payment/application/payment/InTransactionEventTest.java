package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.InTransactionPaymentEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다. 키 없이 보낸다(PaymentEventTest 참고) */
@Tag("known-failing")
@Import(InTransactionPaymentEvents.class)
@TestPropertySource(properties = "spring.kafka.producer.properties.partitioner.class=org.apache.kafka.clients.producer.RoundRobinPartitioner")
@DisplayName("결제 이벤트 — 비교: 상태를 바꾼 그 자리에서 바로 보냄(출발 구조)")
class InTransactionEventTest extends PaymentEventContract {
}
