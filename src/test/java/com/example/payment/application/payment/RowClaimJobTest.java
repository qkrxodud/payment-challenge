package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NetCancelReconciler;
import com.example.payment.application.payment.comparison.RowClaimJobs;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@Import(RowClaimJobs.class)
@DisplayName("서버 여러 대의 주기 작업 — 비교: 결제·알림마다 점유 시각을 두고 서버끼리 나눠 갖기")
class RowClaimJobTest extends MultiServerContract {

    @Override
    Class<?>[] serverComponents() {
        return new Class<?>[] {NetCancelReconciler.class, RowClaimJobs.class};
    }

    @Override
    void reconcileOn(ApplicationContext server, Instant now) {
        server.getBean(RowClaimJobs.class).reconcile(now);
    }

    @Override
    void processWebhooksOn(ApplicationContext server) {
        server.getBean(RowClaimJobs.class).processWebhooks();
    }

    @Override
    void netCancelOn(ApplicationContext server, Instant now) {
        server.getBean(RowClaimJobs.class).netCancel(now);
    }
}
