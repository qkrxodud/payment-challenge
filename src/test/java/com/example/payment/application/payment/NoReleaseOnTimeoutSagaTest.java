package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NoReleaseOnTimeoutRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(NoReleaseOnTimeoutRecorder.class)
@DisplayName("결제와 나눈 재고를 함께 끝내기 — 비교(결정 4): 재고 서비스 답이 없으면 실패로만 보고 해제를 보내지 않음")
class NoReleaseOnTimeoutSagaTest extends SagaContract {

    @Override
    Class<?>[] otherServerComponents() {
        return new Class<?>[]{NoReleaseOnTimeoutRecorder.class};
    }
}
