package com.example.payment.application.payment;

import com.example.payment.FaultInjectingEventSender;
import com.example.payment.SendFaults;
import com.example.payment.application.payment.comparison.RecordFirstOutboxRelay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(RecordFirstOutboxRelay.class)
@DisplayName("결제 결과 릴레이 — 비교: 보낸 것을 먼저 남기고 보냄(최대 한 번)")
class RecordFirstOutboxRelayTest extends OutboxRelayContract {

    @Override
    Class<?>[] serverComponents() {
        return new Class<?>[] {FaultInjectingEventSender.class, RecordFirstOutboxRelay.class};
    }

    // 작업 잠금은 운영과 같다
    @Override
    void relayOn(ApplicationContext server) {
        server.getBean(ExclusiveJobRunner.class).run(ExclusiveJobRunner.OUTBOX, server.getBean(RecordFirstOutboxRelay.class)::relay);
    }

    // 보낸 것으로 남긴 뒤, 보내려던 그 자리에서 죽는다
    @Override
    void crashBetweenSteps(String orderId) {
        SendFaults.crashOnFirstSend(orderId, "APPROVED");
    }

    @Override
    void recoverFromCrash() {
        SendFaults.clear();
    }
}
