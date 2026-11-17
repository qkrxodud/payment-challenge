package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NetCancelReconciler;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.ApplicationContext;

@DisplayName("서버 여러 대의 주기 작업 — 운영 구현")
class MultiServerJobTest extends MultiServerContract {

    // 결정 5부터 작업 잠금을 맡은 서버만 돈다. 주기 실행(PaymentReconciliationScheduler)과 같은 묶음이다
    @Override
    void reconcileOn(ApplicationContext server, Instant now) {
        server.getBean(ExclusiveJobRunner.class).run(ExclusiveJobRunner.RECONCILIATION, () -> {
            server.getBean(PaymentReconciler.class).reconcile(now);
            server.getBean(PaymentCancelReconciler.class).reconcile(now);
        });
    }

    @Override
    void processWebhooksOn(ApplicationContext server) {
        server.getBean(ExclusiveJobRunner.class).run(ExclusiveJobRunner.WEBHOOK,
                server.getBean(PaymentWebhookProcessor.class)::process);
    }

    // 망취소도 4장 보정 자리에서 돌던 것이라 같은 작업 잠금으로 묶는다
    @Override
    void netCancelOn(ApplicationContext server, Instant now) {
        server.getBean(ExclusiveJobRunner.class).run(ExclusiveJobRunner.RECONCILIATION,
                () -> server.getBean(NetCancelReconciler.class).reconcile(now));
    }
}
