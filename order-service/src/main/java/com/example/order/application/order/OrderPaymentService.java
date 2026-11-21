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
        PaymentOutcome outcome = PaymentOutcome.valueOf(command.paymentStatus());
        if (receivedPaymentEventRepository.claim(ReceivedPaymentEvent.of(command.eventId(), command.orderId(),
                command.paymentStatus(), command.occurredAt(), command.partition(), command.offset(), now))) {
            follow(command.orderId(), outcome, now);
        }
    }

    private void follow(String orderId, PaymentOutcome outcome, Instant now) {
        orderRepository.findByOrderId(orderId)
                .ifPresentOrElse(order -> order.follow(outcome, now),
                        () -> orderRepository.save(Order.paidWith(orderId, outcome, now)));
    }
}
