package com.example.order.application.order;

import com.example.order.application.order.dto.PaymentEventCommand;
import com.example.order.domain.order.Order;
import com.example.order.domain.order.OrderRepository;
import com.example.order.domain.order.PaymentOutcome;
import com.example.order.domain.order.ReceivedPaymentEvent;
import com.example.order.domain.order.ReceivedPaymentEventRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 9장: 결제 이벤트를 받아 주문 상태를 바꾼다. 받은 이벤트를 먼저 남기고, 그 주문의 상태를 이벤트의 결제 결과대로 바꾼다.
 * 11장 결정 1: 받은 이벤트는 고유 번호(결제 서버 payment_outbox 번호)가 두 번 오를 수 없는 기록에 남긴다. 같은 트랜잭션이라 주문이 바뀌면 번호도
 * 반드시 남고, 주문이 바뀌지 못하고 되돌려지면 번호도 남지 않아 다시 시도할 수 있다. 이미 남은 번호면 주문을 바꾸지 않는다 —
 * 결제 서버가 같은 이벤트를 다시 보내거나(적어도 한 번, 9장) 주문 서비스가 읽은 자리를 커밋하기 전에 다시 떠 같은 이벤트를 다시 읽어도 한 번만 반영한다.
 * 11장 결정 2: 처음 온 이벤트라도 그 주문에 마지막으로 반영한 이벤트보다 번호가 작으면(먼저 만든 결과가 늦게 옴) 주문을 바꾸지 않는다.
 * 받은 기록에는 남긴다 — 다시 와도 다시 보지 않는다. 주문 줄은 잠그고 읽어, 같은 주문의 이벤트가 겹쳐 처리돼도(소비자 재배치 중) 번호 비교가 어긋나지 않는다.
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final ReceivedPaymentEventRepository receivedPaymentEventRepository;
    private final Clock clock;

    @Transactional
    public void apply(PaymentEventCommand command) {
        Instant now = clock.instant();
        // 11장 결정 3: 영구 오류(모르는 상태, 빠진 값)는 기록을 남기기 전에 알린다 — 다시 시도하지 않는다
        PaymentOutcome outcome = PaymentOutcome.from(command.paymentStatus(), command.eventId());
        long eventNumber = command.eventNumber();
        command.requireComplete();
        if (receivedPaymentEventRepository.claim(ReceivedPaymentEvent.of(command.eventId(), command.orderId(),
                command.paymentStatus(), command.occurredAt(), command.partition(), command.offset(), now))) {
            follow(command.orderId(), outcome, eventNumber, now);
        }
    }

    private void follow(String orderId, PaymentOutcome outcome, long eventId, Instant now) {
        orderRepository.findByOrderIdForUpdate(orderId)
                .ifPresentOrElse(order -> followIfNewer(order, outcome, eventId, now),
                        () -> orderRepository.save(Order.paidWith(orderId, outcome, eventId, now)));
    }

    private static void followIfNewer(Order order, PaymentOutcome outcome, long eventId, Instant now) {
        if (order.isAfterLastApplied(eventId)) {
            order.follow(outcome, eventId, now);
        }
    }
}
