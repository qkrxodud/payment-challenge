package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.ApplicationContext;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("결제 결과 릴레이 — 비교: 잠그지 않음(서버마다 릴레이)")
class NoLockOutboxRelayTest extends OutboxRelayContract {

    @Override
    void relayOn(ApplicationContext server) {
        server.getBean(PaymentOutboxRelay.class).relay();
    }
}
