package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentEvents;
import com.example.payment.domain.error.exception.PaymentEventNotSentException;
import com.example.payment.domain.outbox.PaymentOutboxRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentEvent;
import com.example.payment.domain.payment.PaymentEventSender;
import com.example.payment.domain.payment.PaymentStatus;
import java.time.Clock;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;

/**
 * 비교 구현(9장 결정 1, 출발 구조): 결제 상태를 바꾼 트랜잭션 안에서 바로 브로커에 보내고 받았다는 답을 기다린다. 보내지 못하면 로그만 남긴다.
 * 남겨 두는 것이 없다. 키 없이 보낸다(운영 보내기 그대로 — 결정 4 전까지).
 */
@Slf4j
@Primary
public class InTransactionPaymentEvents extends PaymentEvents {

    private final PaymentEventSender paymentEventSender;
    private final Clock clock;

    public InTransactionPaymentEvents(PaymentOutboxRepository paymentOutboxRepository, PaymentEventSender paymentEventSender,
                                      Clock clock) {
        super(paymentOutboxRepository, clock);
        this.paymentEventSender = paymentEventSender;
        this.clock = clock;
    }

    @Override
    public void publishIfChanged(Payment payment, PaymentStatus before) {
        if (payment.announcesFrom(before)) {
            send(paymentEventSender, PaymentEvent.of(UUID.randomUUID().toString(), payment, clock.instant()));
        }
    }

    static void send(PaymentEventSender sender, PaymentEvent event) {
        try {
            sender.send(event);
        } catch (PaymentEventNotSentException notSent) {
            log.warn("Could not send payment event {} for order {}.", event.getEventId(), event.getOrderId());
        }
    }
}
