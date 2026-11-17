package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.payment.comparison.NoLockPaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(NoLockPaymentRepository.class)
@DisplayName("같은 결제를 바꾸는 동시 기록 — 비교: 잠그지 않음(6장까지)")
class NoLockPaymentLockTest extends PaymentLockContract {
}
