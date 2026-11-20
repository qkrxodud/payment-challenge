package com.example.payment.domain.outbox;

import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentEvent;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.domain.stock.StockCommand;
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
 * 9장 결정 1: 주문 서비스에 알릴 결제 결과 한 건. 결제 상태를 바꾼 그 트랜잭션에서 남긴다 — 상태가 커밋되면 이 기록도 반드시 남고,
 * 커밋이 실패하면 함께 남지 않는다. 브로커에 보내는 일은 따로 한다.
 * 번호(id)가 이벤트의 고유 번호다. 번호는 남긴 순서대로 커지므로, 같은 결제의 이벤트는 번호 순이 만든 순서다.
 * 10장 결정 5부터 재고 서비스에 보낼 재고 명령(예약 확정·해제)도 같은 표, 같은 트랜잭션에 남긴다(type).
 */
@Entity
@Table(name = "payment_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "order_id", nullable = false, updatable = false, length = 64)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 30)
    private PaymentStatus status;

    // 10장 결정 5: 결제 결과(주문 서비스에 알림)인지 재고 명령(재고 서비스에 예약 확정·해제)인지
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private OutboxType type;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    // 브로커가 받았다고 답한 때. 아직 보내지 않았으면 비어 있다
    @Column(name = "sent_at")
    private Instant sentAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentOutbox(Long paymentId, String orderId, PaymentStatus status, OutboxType type, Instant occurredAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = status;
        this.type = type;
        this.occurredAt = occurredAt;
    }

    public static PaymentOutbox of(Payment payment, Instant now) {
        return of(payment, OutboxType.PAYMENT_RESULT, now);
    }

    // 10장 결정 5: 재고 서비스에 보낼 예약 확정·해제. 상태는 명령을 남긴 때의 결제 상태다
    public static PaymentOutbox stockCommand(Payment payment, OutboxType type, Instant now) {
        return of(payment, type, now);
    }

    private static PaymentOutbox of(Payment payment, OutboxType type, Instant now) {
        return PaymentOutbox.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .status(payment.getStatus())
                .type(type)
                .occurredAt(now)
                .build();
    }

    public boolean isStockCommand() {
        return type.isStockCommand();
    }

    public StockCommand toStockCommand() {
        return StockCommand.of(String.valueOf(id), paymentId, orderId, type);
    }

    public PaymentEvent toEvent() {
        return PaymentEvent.of(String.valueOf(id), paymentId, orderId, status, occurredAt);
    }
}
