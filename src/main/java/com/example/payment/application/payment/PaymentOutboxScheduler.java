package com.example.payment.application.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 9장 결정 2: 남긴 결제 결과를 보내는 릴레이를 정해진 주기마다 돌린다. 앞 주기가 끝난 뒤 payment.outbox.interval 만큼 쉬고 다시 돈다.
 * 결제 이벤트 계약 테스트 밖의 테스트는 payment.outbox.enabled=false 로 끈다(보낼 것이 남아도 보내지 않는다).
 * 결정 3: 서버가 여러 대여도 작업 잠금(7장 job_lock)을 맡은 한 대만 돈다. 한 대가 번호 순으로 보내야 같은 결제의 이벤트가 만든 순서대로 나가고,
 * 같은 결과를 서버마다 한 번씩 보내지 않는다. 7장 주기 작업과 같은 방식을 이어 쓴다.
 * 비용: 보내는 양을 서버 수만큼 늘릴 수 없다 — 한 주기에 보내는 서버는 언제나 한 대다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentOutboxScheduler {

    private final ExclusiveJobRunner exclusiveJobRunner;
    private final PaymentOutboxRelay paymentOutboxRelay;

    @Scheduled(fixedDelayString = "${payment.outbox.interval}")
    public void relay() {
        exclusiveJobRunner.run(ExclusiveJobRunner.OUTBOX, paymentOutboxRelay::relay);
    }
}
