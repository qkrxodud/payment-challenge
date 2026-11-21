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
 * 9장의 주문 서비스는 받은 순서대로 마지막 이벤트가 주문 상태를 정했다 — 늦게 온 옛 이벤트도 그대로 반영했다.
 * 11장 결정 2: 주문은 마지막으로 반영한 이벤트의 고유 번호(결제 서버 payment_outbox 번호)를 쥔다. 그보다 번호가 크지 않은 이벤트는 반영하지 않는다 —
 * 같은 결제의 이벤트는 결제 줄을 잠근 기록(7장)이 차례로 남겨 번호가 만든 순서대로 커지므로(9장), 번호가 작으면 이미 반영한 결과보다 먼저 만든 결과다.
 * 만든 시각(occurredAt)으로 비교하지 않는 이유: 결제 서버가 여러 대면(7장) 서버마다 시계가 달라 시각이 만든 순서와 어긋날 수 있다.
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

    // 11장 결정 2: 마지막으로 반영한 결제 이벤트의 고유 번호
    @Column(name = "last_event_id", nullable = false)
    private long lastEventId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private Order(String orderId, PaymentOutcome paymentStatus, long lastEventId, Instant updatedAt) {
        this.orderId = orderId;
        this.paymentStatus = paymentStatus;
        this.status = paymentStatus.getOrderStatus();
        this.lastEventId = lastEventId;
        this.updatedAt = updatedAt;
    }

    // 이 주문의 결제 이벤트가 처음 왔다
    public static Order paidWith(String orderId, PaymentOutcome outcome, long eventId, Instant now) {
        return Order.builder()
                .orderId(orderId)
                .paymentStatus(outcome)
                .lastEventId(eventId)
                .updatedAt(now)
                .build();
    }

    // 11장 결정 2: 마지막으로 반영한 이벤트보다 뒤에 만든 이벤트인가(번호가 더 큰가)
    public boolean isAfterLastApplied(long eventId) {
        return eventId > lastEventId;
    }

    // 받은 결제 결과대로 주문 상태를 바꾸고 그 이벤트의 번호를 마지막으로 반영한 번호로 남긴다.
    // 옛 이벤트인지(isAfterLastApplied)는 부르는 쪽이 먼저 본다
    public void follow(PaymentOutcome outcome, long eventId, Instant now) {
        this.paymentStatus = outcome;
        this.status = outcome.getOrderStatus();
        this.lastEventId = eventId;
        this.updatedAt = now;
    }
}
