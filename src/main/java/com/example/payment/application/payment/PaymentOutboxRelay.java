package com.example.payment.application.payment;

import com.example.payment.domain.outbox.PaymentOutbox;
import com.example.payment.domain.outbox.PaymentOutboxRepository;
import com.example.payment.domain.payment.PaymentEventSender;
import com.example.payment.global.config.OutboxProperties;
import com.example.payment.global.error.exception.PaymentEventNotSentException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 9장 결정 2: 남긴 결제 결과(payment_outbox)를 브로커에 보내는 릴레이. 아직 보내지 않은 결과를 번호 순으로 읽어 하나씩 보내고,
 * 브로커가 받았다고 답하면 보낸 때를 남긴다. 정해진 주기마다 돈다(PaymentOutboxScheduler).
 * 보내지 못하면 이번 주기를 거기서 멈춘다 — 뒤의 결과를 먼저 보내지 않고, 다음 주기에 그 결과부터 다시 보낸다.
 * 비용: 결과는 길게는 한 주기만큼 늦게 나가고, 보낼 것이 없어도 주기마다 DB 를 읽는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutboxRelay {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentEventSender paymentEventSender;
    private final OutboxProperties outboxProperties;
    private final Clock clock;

    public void relay() {
        for (PaymentOutbox outbox : paymentOutboxRepository.findUnsent(outboxProperties.getBatchSize())) {
            if (isNotSent(outbox)) {
                return;
            }
        }
    }

    private boolean isNotSent(PaymentOutbox outbox) {
        try {
            paymentEventSender.send(outbox.toEvent());
        } catch (PaymentEventNotSentException notSent) {
            log.warn("Could not send payment event {}. It will be sent again next time.", outbox.getId());
            return true;
        }
        paymentOutboxRepository.markSent(outbox.getId(), clock.instant());
        return false;
    }
}
