package com.example.payment.application.payment;

import com.example.payment.FaultInjectingEventSender;
import com.example.payment.application.payment.comparison.SkipLockedOutboxRelay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(SkipLockedOutboxRelay.class)
@DisplayName("결제 결과 릴레이 — 비교: 한 줄씩 잠가 나눠 보내기(select … for update skip locked)")
class SkipLockedOutboxRelayTest extends OutboxRelayContract {

    @Override
    Class<?>[] serverComponents() {
        return new Class<?>[] {FaultInjectingEventSender.class, SkipLockedOutboxRelay.class};
    }

    @Override
    void relayOn(ApplicationContext server) {
        server.getBean(SkipLockedOutboxRelay.class).relay();
    }
}
