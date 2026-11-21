package com.example.payment.application.payment.comparison;

import com.example.order.application.order.OrderPaymentService;
import com.example.order.application.order.dto.PaymentEventCommand;
import com.example.order.domain.order.Order;
import com.example.order.domain.order.OrderRepository;
import com.example.order.domain.order.PaymentOutcome;
import com.example.order.domain.order.ReceivedPaymentEvent;
import com.example.order.domain.order.ReceivedPaymentEventRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 구현(11장 결정 2, 거르지 않음): 같은 이벤트는 결정 1대로 한 번만 받지만, 처음 온 이벤트면 번호가 앞선 옛 결과라도 그대로 따른다 —
 * 마지막으로 받은 결과가 주문 상태를 정한다(9장 방식). 취소 뒤에 그보다 먼저 만든 승인이 늦게 오면 주문은 결제 완료로 되돌아간다.
 */
@Primary
public class NoFilterOrderPaymentService extends OrderPaymentService {

    private final OrderRepository orders;
    private final ReceivedPaymentEventRepository received;
    private final Clock clock;

    public NoFilterOrderPaymentService(OrderRepository orderRepository, ReceivedPaymentEventRepository receivedPaymentEventRepository,
                                       Clock clock) {
        super(orderRepository, receivedPaymentEventRepository, clock);
        this.orders = orderRepository;
        this.received = receivedPaymentEventRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void apply(PaymentEventCommand command) {
        Instant now = clock.instant();
        // 11장 결정 3(영구 오류는 다시 시도하지 않음)은 운영과 같게 둔다
        PaymentOutcome outcome = PaymentOutcome.from(command.paymentStatus(), command.eventId());
        command.eventNumber();
        command.requireComplete();
        if (received.claim(ReceivedPaymentEvent.of(command.eventId(), command.orderId(), command.paymentStatus(), command.occurredAt(),
                command.partition(), command.offset(), now))) {
            orders.findByOrderIdForUpdate(command.orderId())
                    .ifPresentOrElse(order -> order.follow(outcome, command.eventNumber(), now),
                            () -> orders.save(Order.paidWith(command.orderId(), outcome, command.eventNumber(), now)));
        }
    }
}
