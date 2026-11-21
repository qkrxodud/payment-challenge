package com.example.payment.application.payment.comparison;

import com.example.order.application.order.OrderPaymentService;
import com.example.order.application.order.dto.PaymentEventCommand;
import com.example.order.domain.order.Order;
import com.example.order.domain.order.OrderRepository;
import com.example.order.domain.order.PaymentOutcome;
import com.example.order.domain.order.ReceivedPaymentEvent;
import com.example.order.domain.order.ReceivedPaymentEventRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 구현(11장 결정 2, 발생 시각으로 비교): 같은 이벤트는 결정 1대로 한 번만 받고, 이 주문에 앞서 받은 이벤트 가운데 만든 시각(occurredAt)이
 * 같거나 더 늦은 것이 있으면 옛 결과로 보고 주문을 바꾸지 않는다. 시각은 결제 서버가 이벤트를 남긴 서버의 시계다 —
 * 서버마다 시계가 다르면(7장 서버 두 대) 뒤에 만든 결과의 시각이 앞설 수 있다.
 */
@Primary
public class OccurredAtOrderPaymentService extends OrderPaymentService {

    private final OrderRepository orders;
    private final ReceivedPaymentEventRepository received;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public OccurredAtOrderPaymentService(OrderRepository orderRepository,
                                         ReceivedPaymentEventRepository receivedPaymentEventRepository, Clock clock,
                                         JdbcTemplate jdbcTemplate) {
        super(orderRepository, receivedPaymentEventRepository, clock);
        this.orders = orderRepository;
        this.received = receivedPaymentEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void apply(PaymentEventCommand command) {
        Instant now = clock.instant();
        PaymentOutcome outcome = PaymentOutcome.valueOf(command.paymentStatus());
        if (received.claim(ReceivedPaymentEvent.of(command.eventId(), command.orderId(), command.paymentStatus(), command.occurredAt(),
                command.partition(), command.offset(), now))) {
            orders.findByOrderIdForUpdate(command.orderId())
                    .ifPresentOrElse(order -> followIfLater(order, command, outcome, now),
                            () -> orders.save(Order.paidWith(command.orderId(), outcome, command.eventNumber(), now)));
        }
    }

    private void followIfLater(Order order, PaymentEventCommand command, PaymentOutcome outcome, Instant now) {
        if (isLatest(command)) {
            order.follow(outcome, command.eventNumber(), now);
        }
    }

    private boolean isLatest(PaymentEventCommand command) {
        Integer laterOrSame = jdbcTemplate.queryForObject("select count(*) from received_payment_event "
                        + "where order_id = ? and event_id <> ? and occurred_at >= ?", Integer.class,
                command.orderId(), command.eventId(), Timestamp.from(command.occurredAt()));
        return Objects.requireNonNullElse(laterOrSame, 0) == 0;
    }
}
