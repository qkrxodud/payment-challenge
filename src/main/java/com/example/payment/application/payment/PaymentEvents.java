package com.example.payment.application.payment;

import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentEvent;
import com.example.payment.domain.payment.PaymentEventSender;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.PaymentEventNotSentException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 9장: 결제 결과를 주문 서비스에 알린다. 결제를 바꾸는 기록(PaymentRecorder 등)이 결제 상태를 바꾼 바로 뒤에 부른다.
 * 결제가 결과 상태로 바뀌었으면 그 자리에서 이벤트를 브로커에 보내고, 받았다는 답을 기다린다. 보내지 못하면 로그만 남긴다 —
 * 이벤트 때문에 결제 기록을 되돌리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEvents {

    private final PaymentEventSender paymentEventSender;
    private final Clock clock;

    public void publishIfChanged(Payment payment, PaymentStatus before) {
        if (payment.announcesFrom(before)) {
            publish(PaymentEvent.of(UUID.randomUUID().toString(), payment, clock.instant()));
        }
    }

    private void publish(PaymentEvent event) {
        try {
            paymentEventSender.send(event);
        } catch (PaymentEventNotSentException notSent) {
            log.warn("Could not send payment event {} for order {}.", event.getEventId(), event.getOrderId());
        }
    }
}
