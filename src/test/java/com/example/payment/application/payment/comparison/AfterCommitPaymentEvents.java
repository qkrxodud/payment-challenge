package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentEvents;
import com.example.payment.domain.outbox.PaymentOutboxRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentEvent;
import com.example.payment.domain.payment.PaymentEventSender;
import com.example.payment.domain.payment.PaymentStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 비교 구현(9장 결정 1): 커밋 뒤에 보낸다. 결제 상태를 바꾼 트랜잭션 안에서는 이벤트를 Spring 이벤트로만 내고,
 * 커밋이 끝난 뒤 @TransactionalEventListener(AFTER_COMMIT) 가 브로커에 보내고 받았다는 답을 기다린다. 보내지 못하면 로그만 남긴다.
 * 남겨 두는 것이 없다. 키 없이 보낸다.
 */
@Primary
public class AfterCommitPaymentEvents extends PaymentEvents {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final PaymentEventSender paymentEventSender;
    private final Clock clock;

    public AfterCommitPaymentEvents(PaymentOutboxRepository paymentOutboxRepository, ApplicationEventPublisher applicationEventPublisher,
                                    PaymentEventSender paymentEventSender, Clock clock) {
        super(paymentOutboxRepository, clock);
        this.applicationEventPublisher = applicationEventPublisher;
        this.paymentEventSender = paymentEventSender;
        this.clock = clock;
    }

    @Override
    public void publishIfChanged(Payment payment, PaymentStatus before) {
        if (payment.announcesFrom(before)) {
            applicationEventPublisher.publishEvent(PaymentEvent.of(UUID.randomUUID().toString(), payment, clock.instant()));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(PaymentEvent event) {
        InTransactionPaymentEvents.send(paymentEventSender, event);
    }
}
