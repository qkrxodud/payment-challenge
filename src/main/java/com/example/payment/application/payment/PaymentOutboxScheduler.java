package com.example.payment.application.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 9장 결정 2: 남긴 결제 결과를 보내는 릴레이를 정해진 주기마다 돌린다. 앞 주기가 끝난 뒤 payment.outbox.interval 만큼 쉬고 다시 돈다.
 * 결제 이벤트 계약 테스트 밖의 테스트는 payment.outbox.enabled=false 로 끈다(보낼 것이 남아도 보내지 않는다).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentOutboxScheduler {

    private final PaymentOutboxRelay paymentOutboxRelay;

    @Scheduled(fixedDelayString = "${payment.outbox.interval}")
    public void relay() {
        paymentOutboxRelay.relay();
    }
}
