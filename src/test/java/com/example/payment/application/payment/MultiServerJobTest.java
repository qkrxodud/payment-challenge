package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NetCancelReconciler;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.ApplicationContext;

@DisplayName("서버 여러 대의 주기 작업 — 운영 구현")
class MultiServerJobTest extends MultiServerContract {

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
