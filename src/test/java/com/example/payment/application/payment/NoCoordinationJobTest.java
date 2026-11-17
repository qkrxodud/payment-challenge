package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NetCancelReconciler;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.ApplicationContext;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("서버 여러 대의 주기 작업 — 비교: 나누지 않음(서버마다 모든 대상을 돎, 6장까지)")
class NoCoordinationJobTest extends MultiServerContract {

    @Override
    void reconcileOn(ApplicationContext server, Instant now) {
        server.getBean(PaymentReconciler.class).reconcile(now);
        server.getBean(PaymentCancelReconciler.class).reconcile(now);
    }

    @Override
    void processWebhooksOn(ApplicationContext server) {
        server.getBean(PaymentWebhookProcessor.class).process();
    }

    @Override
    void netCancelOn(ApplicationContext server, Instant now) {
        server.getBean(NetCancelReconciler.class).reconcile(now);
    }
}
