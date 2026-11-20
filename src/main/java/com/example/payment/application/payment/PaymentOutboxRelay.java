package com.example.payment.application.payment;

import com.example.payment.domain.error.exception.PaymentEventNotSentException;
import com.example.payment.domain.outbox.PaymentOutbox;
import com.example.payment.domain.outbox.PaymentOutboxRepository;
import com.example.payment.domain.payment.PaymentEventSender;
import com.example.payment.global.config.OutboxProperties;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 9장 결정 2: 남긴 결제 결과(payment_outbox)를 브로커에 보내는 릴레이. 아직 보내지 않은 결과를 번호 순으로 읽어 하나씩 보내고,
 * 브로커가 받았다고 답하면 보낸 때를 남긴다. 정해진 주기마다 돈다(PaymentOutboxScheduler).
 * 비용: 결과는 길게는 한 주기만큼 늦게 나가고, 보낼 것이 없어도 주기마다 DB 를 읽는다.
 * 결정 4: 한 결제의 결과를 보내지 못하면 이번 주기에는 그 결제의 다음 결과를 보내지 않고 기다린다(다음 주기에 앞 결과부터 다시) —
 * 같은 결제의 이벤트가 만든 순서대로 나간다. 다른 결제의 결과는 이어서 보낸다(결정 2는 한 건만 못 보내도 주기를 멈췄다).
 * 결정 5: 보장은 적어도 한 번이다. 먼저 보내고, 받았다는 답이 온 뒤에 보낸 것을 남긴다 — 그 사이에 릴레이가 죽거나 남기기가 실패하면
 * 다음 주기에 같은 결과를 다시 보내 같은 이벤트가 두 번 갈 수 있다. 대신 남긴 결과를 잃지는 않는다. 이벤트의 고유 번호(남긴 결과의 번호)로
 * 받는 쪽이 같은 이벤트를 가릴 수 있다(가리는 일은 11장). 받았다는 답을 못 받았어도 브로커에는 남았을 수 있어, 그때 다시 보내도 중복이 생긴다.
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
        // 이번 주기에 결과를 보내지 못해 다음 결과를 기다리게 한 결제
        Set<Long> waiting = new HashSet<>();
        for (PaymentOutbox outbox : paymentOutboxRepository.findUnsent(outboxProperties.getBatchSize())) {
            relayUnlessWaiting(outbox, waiting);
        }
    }

    private void relayUnlessWaiting(PaymentOutbox outbox, Set<Long> waiting) {
        if (waiting.contains(outbox.getPaymentId())) {
            return;
        }
        if (isNotSent(outbox)) {
            waiting.add(outbox.getPaymentId());
        }
    }

    private boolean isNotSent(PaymentOutbox outbox) {
        try {
            paymentEventSender.send(outbox.toEvent());
        } catch (PaymentEventNotSentException notSent) {
            log.warn("Could not send payment event {}. It will be sent again next time.", outbox.getId());
            return true;
        }
        // 결정 5: 받았다는 답이 온 뒤에만 남긴다. 이 기록이 실패하면 주기가 거기서 끝나고, 다음 주기에 다시 보낸다
        paymentOutboxRepository.markSent(outbox.getId(), clock.instant());
        return false;
    }
}
