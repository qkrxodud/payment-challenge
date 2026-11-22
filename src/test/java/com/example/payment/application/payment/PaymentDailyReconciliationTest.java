package com.example.payment.application.payment;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.ApplicationContext;

@DisplayName("하루치 대사 — 운영 구현")
class PaymentDailyReconciliationTest extends DailyReconciliationContract {

    @Override
    void reconcile(LocalDate day) {
        reconcileOn(thisServer, day);
    }

    // 12장 출발 구조: 대사가 없다. 서버가 가진 맞추기는 4장 보정(결과 모름·인증 상태), 5장 취소 보정(결과를 기다리는 취소),
    // 6장 받은 알림 처리뿐이다 — 셋을 모두 한 번 돌린다(보정 대기 시간이 지난 때로)
    @Override
    void reconcileOn(ApplicationContext server, LocalDate day) {
        Instant afterWaiting = Instant.now().plus(Duration.ofMinutes(1));
        server.getBean(ExclusiveJobRunner.class).run(ExclusiveJobRunner.RECONCILIATION, () -> {
            server.getBean(PaymentReconciler.class).reconcile(afterWaiting);
            server.getBean(PaymentCancelReconciler.class).reconcile(afterWaiting);
        });
        server.getBean(ExclusiveJobRunner.class).run(ExclusiveJobRunner.WEBHOOK, server.getBean(PaymentWebhookProcessor.class)::process);
    }

    // 12장 출발 구조: 대사 결과를 남기는 곳이 없다
    @Override
    List<ReconciliationLine> resultsOf(LocalDate day) {
        return List.of();
    }
}
