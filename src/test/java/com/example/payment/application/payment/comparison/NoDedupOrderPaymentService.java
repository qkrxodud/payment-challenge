package com.example.payment.application.payment.comparison;

import com.example.order.application.order.OrderPaymentService;
import com.example.order.application.order.dto.PaymentEventCommand;
import com.example.order.domain.order.Order;
import com.example.order.domain.order.OrderRepository;
import com.example.order.domain.order.PaymentOutcome;
import com.example.order.domain.order.ReceivedPaymentEventRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 구현(11장 결정 1, 가리지 않음 — 9·10장의 주문 서비스): 받은 결제 이벤트를 고유 번호와 상관없이 모두 남기고(같은 번호도 한 줄 더),
 * 받은 결과대로 주문을 바꾼다. 같은 이벤트가 다시 오면 받은 기록이 두 줄이 되고 주문도 다시 바뀐다.
 * 주문 서비스에 끼우는 시험 구성품이다(test.order.components). 받은 기록의 고유 키는 시험 스키마(order-no-unique-event.sql)로 뗀다.
 */
@Primary
public class NoDedupOrderPaymentService extends OrderPaymentService {

    private final OrderRepository orders;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public NoDedupOrderPaymentService(OrderRepository orderRepository, ReceivedPaymentEventRepository receivedPaymentEventRepository,
                                      Clock clock, JdbcTemplate jdbcTemplate) {
        super(orderRepository, receivedPaymentEventRepository, clock);
        this.orders = orderRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void apply(PaymentEventCommand command) {
        Instant now = clock.instant();
        PaymentOutcome outcome = PaymentOutcome.valueOf(command.paymentStatus());
        record(jdbcTemplate, command, now);
        orders.findByOrderIdForUpdate(command.orderId())
                .ifPresentOrElse(order -> followIfNewer(order, outcome, command.eventNumber(), now),
                        () -> orders.save(Order.paidWith(command.orderId(), outcome, command.eventNumber(), now)));
    }

    // 결정 2(번호로 옛 결과 거르기)는 그대로 둔다
    private static void followIfNewer(Order order, PaymentOutcome outcome, long eventId, Instant now) {
        if (order.isAfterLastApplied(eventId)) {
            order.follow(outcome, eventId, now);
        }
    }

    // 받은 이벤트를 가리지 않고 한 줄 남긴다
    static void record(JdbcTemplate jdbcTemplate, PaymentEventCommand command, Instant now) {
        jdbcTemplate.update("insert into received_payment_event "
                        + "(event_id, order_id, payment_status, occurred_at, partition_no, offset_no, received_at) values (?, ?, ?, ?, ?, ?, ?)",
                command.eventId(), command.orderId(), command.paymentStatus(), Timestamp.from(command.occurredAt()),
                command.partition(), command.offset(), Timestamp.from(now));
    }
}
