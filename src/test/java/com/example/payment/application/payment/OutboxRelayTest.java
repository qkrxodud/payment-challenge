package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.springframework.context.ApplicationContext;

@DisplayName("결제 결과 릴레이 — 운영 구현")
class OutboxRelayTest extends OutboxRelayContract {

    // 결정 3부터 작업 잠금을 맡은 서버만 돈다. 주기 실행(PaymentOutboxScheduler)과 같은 묶음이다
    @Override
    void relayOn(ApplicationContext server) {
        server.getBean(ExclusiveJobRunner.class).run(ExclusiveJobRunner.OUTBOX, server.getBean(PaymentOutboxRelay.class)::relay);
    }
}
