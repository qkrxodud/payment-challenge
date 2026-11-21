package com.example.payment.application.payment.comparison;

import com.example.order.application.order.OrderPaymentService;
import com.example.order.application.order.dto.PaymentEventCommand;
import com.example.order.domain.order.Order;
import com.example.order.domain.order.OrderRepository;
import com.example.order.domain.order.PaymentOutcome;
import com.example.order.domain.order.ReceivedPaymentEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 구현(11장 결정 1, 상태 전이 규칙만으로 흡수 — 10장 재고 서비스 방식): 받은 이벤트는 9장처럼 모두 남기고(고유 번호로 가리지 않음),
 * 주문은 결제 상태가 갈 수 있는 방향(결제 서버의 허용 전이 표 가운데 결과 상태끼리: 승인 → 매입·승인 취소, 매입 → 매입 취소)일 때만 바꾼다.
 * 같은 결과가 다시 오면 같은 상태로는 갈 수 없어 주문은 그대로지만 받은 기록은 한 줄 더 남는다.
 */
@Primary
public class TransitionOnlyOrderPaymentService extends OrderPaymentService {

    private static final Map<PaymentOutcome, Set<PaymentOutcome>> NEXT = Map.of(
            PaymentOutcome.APPROVED, Set.of(PaymentOutcome.CAPTURED, PaymentOutcome.APPROVAL_CANCELED),
            PaymentOutcome.CAPTURED, Set.of(PaymentOutcome.CAPTURE_CANCELED));

    private final OrderRepository orders;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public TransitionOnlyOrderPaymentService(OrderRepository orderRepository,
                                             ReceivedPaymentEventRepository receivedPaymentEventRepository, Clock clock,
                                             JdbcTemplate jdbcTemplate) {
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
        NoDedupOrderPaymentService.record(jdbcTemplate, command, now);
        orders.findByOrderId(command.orderId())
                .ifPresentOrElse(order -> followIfAllowed(order, outcome, now),
                        () -> orders.save(Order.paidWith(command.orderId(), outcome, now)));
    }

    private static void followIfAllowed(Order order, PaymentOutcome outcome, Instant now) {
        if (NEXT.getOrDefault(order.getPaymentStatus(), Set.of()).contains(outcome)) {
            order.follow(outcome, now);
        }
    }
}
