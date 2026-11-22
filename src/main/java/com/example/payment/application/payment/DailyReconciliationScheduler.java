package com.example.payment.application.payment;

import com.example.payment.global.config.DailyReconciliationProperties;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 12장 결정 1: 매일 정한 때(payment.daily-reconciliation.cron, 대사 시간대 기준)에 어제의 대사를 돌린다.
 * 서버마다 이 일정이 돌지만 작업 잠금을 맡은 한 대만 대사한다(DailyReconciler).
 * 테스트는 대사를 직접 부르므로 payment.daily-reconciliation.enabled=false 로 끈다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.daily-reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DailyReconciliationScheduler {

    private final DailyReconciler dailyReconciler;
    private final DailyReconciliationProperties dailyReconciliationProperties;
    private final Clock clock;

    @Scheduled(cron = "${payment.daily-reconciliation.cron}", zone = "${payment.daily-reconciliation.zone}")
    public void reconcileYesterday() {
        dailyReconciler.reconcile(LocalDate.now(clock.withZone(dailyReconciliationProperties.getZone())).minusDays(1));
    }
}
