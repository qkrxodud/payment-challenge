package com.example.payment.application.payment;

import com.example.payment.domain.outbox.PaymentOutbox;
import com.example.payment.domain.outbox.PaymentOutboxRepository;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatus;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 9장: 결제 결과를 주문 서비스에 알린다. 결제를 바꾸는 기록(PaymentRecorder 등)이 결제 상태를 바꾼 바로 뒤, 같은 트랜잭션 안에서 부른다.
 * 결정 1: 결제가 결과 상태로 바뀌었으면 알릴 결과를 payment_outbox 에 한 줄 남긴다. 결제 상태와 한꺼번에 반영되므로, 상태가 커밋되면
 * 알릴 결과도 반드시 남고, 커밋이 실패하면 함께 남지 않는다. 브로커에 보내는 일은 여기서 하지 않는다 — 브로커가 끊기거나 느려도
 * 결제 기록과 결제 API 응답이 기다리지 않는다. 대신 남긴 결과를 따로 보내는 일이 있어야 한다.
 * (출발 구조는 이 자리에서 바로 브로커에 보냈다: 커밋 전에 보내 커밋이 실패해도 이벤트는 나가고, 보내기가 실패하면 이벤트를 잃고,
 * 브로커가 느리면 결제 기록이 결제 줄을 잠근 채 기다렸다)
 */
@Component
@RequiredArgsConstructor
public class PaymentEvents {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final Clock clock;

    public void publishIfChanged(Payment payment, PaymentStatus before) {
        if (payment.announcesFrom(before)) {
            paymentOutboxRepository.save(PaymentOutbox.of(payment, clock.instant()));
        }
    }
}
