package com.example.payment.application.payment;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결과 모름 보정을 정해진 주기마다 돌린다(4장). 앞 보정이 끝난 뒤 payment.reconciliation.interval 만큼 쉬고 다시 돈다.
 * 5장부터 승인 보정 뒤에 취소 보정도 돌린다.
 * 테스트는 보정을 직접 부르므로 payment.reconciliation.enabled=false 로 끈다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationScheduler {

    private final PaymentReconciler paymentReconciler;
    private final PaymentCancelReconciler paymentCancelReconciler;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${payment.reconciliation.interval}")
    public void reconcile() {
        paymentReconciler.reconcile(clock.instant());
        paymentCancelReconciler.reconcile(clock.instant());
    }
}
