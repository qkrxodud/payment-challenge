package com.example.payment.application.payment.comparison;

import com.example.payment.domain.outbox.PaymentOutbox;
import com.example.payment.domain.outbox.PaymentOutboxRepository;
import com.example.payment.domain.payment.PaymentEventSender;
import com.example.payment.global.config.OutboxProperties;
import com.example.payment.global.error.exception.PaymentEventNotSentException;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * 비교 구현(9장 결정 5): 보낸 것을 먼저 남기고 보낸다(최대 한 번). 보내기 전에 보낸 것으로 남기므로 같은 결과를 두 번 보내지 않지만,
 * 남긴 뒤 보내다 죽거나 보내지 못하면 그 결과는 다시 보내지 않는다. 그 밖(번호 순, 보내지 못한 결제의 다음 결과는 기다림)은 운영 릴레이와 같다.
 */
@Slf4j
public class RecordFirstOutboxRelay {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentEventSender paymentEventSender;
    private final OutboxProperties outboxProperties;
    private final Clock clock;

    public RecordFirstOutboxRelay(PaymentOutboxRepository paymentOutboxRepository, PaymentEventSender paymentEventSender,
                                  OutboxProperties outboxProperties, Clock clock) {
        this.paymentOutboxRepository = paymentOutboxRepository;
        this.paymentEventSender = paymentEventSender;
        this.outboxProperties = outboxProperties;
        this.clock = clock;
    }

    public void relay() {
        Set<Long> waiting = new HashSet<>();
        for (PaymentOutbox outbox : paymentOutboxRepository.findUnsent(outboxProperties.getBatchSize())) {
            relayUnlessWaiting(outbox, waiting);
        }
    }

    private void relayUnlessWaiting(PaymentOutbox outbox, Set<Long> waiting) {
        if (waiting.contains(outbox.getPaymentId())) {
            return;
        }
        paymentOutboxRepository.markSent(outbox.getId(), clock.instant());
        try {
            paymentEventSender.send(outbox.toEvent());
        } catch (PaymentEventNotSentException notSent) {
            log.warn("Could not send payment event {}. It is already recorded as sent.", outbox.getId());
            waiting.add(outbox.getPaymentId());
        }
    }
}
