package com.example.order.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 9장: 주문. 결제 이벤트가 오면 그 결제 결과대로 주문 상태를 바꾼다.
 * 이 장의 주문 서비스는 단순하게 받는다 — 받은 순서대로 마지막 이벤트가 주문 상태를 정한다. 늦게 온 옛 이벤트나 같은 이벤트가 다시 와도
 * 가리지 않는다(가리는 일은 11장). 그래서 결제 서버가 같은 결제의 이벤트를 만든 순서대로 보내야 주문 상태가 결제와 맞는다.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false, length = 64)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    // 마지막으로 받은 결제 결과
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentOutcome paymentStatus;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Order(String orderId, PaymentOutcome paymentStatus, Instant updatedAt) {
        this.orderId = orderId;
        this.paymentStatus = paymentStatus;
        this.status = paymentStatus.getOrderStatus();
        this.updatedAt = updatedAt;
    }

    // 이 주문의 결제 이벤트가 처음 왔다
    public static Order paidWith(String orderId, PaymentOutcome outcome, Instant now) {
        return Order.builder()
                .orderId(orderId)
                .paymentStatus(outcome)
                .updatedAt(now)
                .build();
    }

    // 받은 결제 결과대로 주문 상태를 바꾼다. 앞서 받은 결과보다 오래된 결과인지는 보지 않는다
    public void follow(PaymentOutcome outcome, Instant now) {
        this.paymentStatus = outcome;
        this.status = outcome.getOrderStatus();
        this.updatedAt = now;
    }
}
