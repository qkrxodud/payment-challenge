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
 * 9장: 결제 이벤트를 받아 주문 상태를 바꾼다. 받은 이벤트를 먼저 남기고(중복도 그대로), 그 주문의 상태를 이벤트의 결제 결과대로 바꾼다.
 * 중복을 가리거나, 순서가 바뀐 이벤트를 거르거나, 실패한 이벤트를 따로 모으는 일은 하지 않는다(11장).
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
        receivedPaymentEventRepository.save(ReceivedPaymentEvent.of(command.eventId(), command.orderId(),
                command.paymentStatus(), command.occurredAt(), command.partition(), command.offset(), now));
        PaymentOutcome outcome = PaymentOutcome.valueOf(command.paymentStatus());
        orderRepository.findByOrderId(command.orderId())
                .ifPresentOrElse(order -> order.follow(outcome, now),
                        () -> orderRepository.save(Order.paidWith(command.orderId(), outcome, now)));
    }
}
