package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.RecordOnlyResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(RecordOnlyResolver.class)
@DisplayName("하루치 대사 — 비교(결정 2): 기록만 하고 고치지 않음")
class RecordOnlyReconciliationTest extends PaymentDailyReconciliationTest {
}
