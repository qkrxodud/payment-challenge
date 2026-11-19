package com.example.order.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 9장: 받은 결제 이벤트 한 건마다 한 줄. 같은 이벤트(같은 고유 번호)가 다시 와도 그대로 한 줄 더 남긴다 —
 * 이 장에서는 중복을 가리지 않고, 몇 번 왔는지 셀 수 있게만 둔다(11장).
 */
@Entity
@Table(name = "received_payment_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivedPaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 결제 서버가 이벤트마다 붙인 고유 번호
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "payment_status", nullable = false, length = 30)
    private String paymentStatus;

    // 결제 서버가 이 이벤트를 만든 때
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // 이 이벤트가 실려 온 파티션과 그 안의 위치
    @Column(name = "partition_no", nullable = false)
    private int partitionNo;

    @Column(name = "offset_no", nullable = false)
    private long offsetNo;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private ReceivedPaymentEvent(String eventId, String orderId, String paymentStatus, Instant occurredAt, int partitionNo,
                                 long offsetNo, Instant receivedAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.paymentStatus = paymentStatus;
        this.occurredAt = occurredAt;
        this.partitionNo = partitionNo;
        this.offsetNo = offsetNo;
        this.receivedAt = receivedAt;
    }

    public static ReceivedPaymentEvent of(String eventId, String orderId, String paymentStatus, Instant occurredAt,
                                          int partitionNo, long offsetNo, Instant receivedAt) {
        return ReceivedPaymentEvent.builder()
                .eventId(eventId)
                .orderId(orderId)
                .paymentStatus(paymentStatus)
                .occurredAt(occurredAt)
                .partitionNo(partitionNo)
                .offsetNo(offsetNo)
                .receivedAt(receivedAt)
                .build();
    }
}
