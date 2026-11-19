package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.AfterCommitPaymentEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다. 키 없이 보낸다(PaymentEventTest 참고) */
@Tag("known-failing")
@Import(AfterCommitPaymentEvents.class)
@TestPropertySource(properties = "spring.kafka.producer.properties.partitioner.class=org.apache.kafka.clients.producer.RoundRobinPartitioner")
@DisplayName("결제 이벤트 — 비교: 커밋 뒤에 보냄")
class AfterCommitEventTest extends PaymentEventContract {
}
