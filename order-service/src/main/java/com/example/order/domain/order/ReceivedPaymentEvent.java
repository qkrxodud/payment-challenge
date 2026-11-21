package com.example.order.domain.order;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 9장: 받은 결제 이벤트 한 건. 9장에서는 같은 이벤트(같은 고유 번호)가 다시 와도 한 줄 더 남겼다.
 * 11장 결정 1: 받은 이벤트 기록이 곧 처리한 이벤트 기록이다 — 고유 번호가 두 번 오를 수 없어, 같은 이벤트가 다시 오면 남지 않고 주문도 바꾸지 않는다.
 * 기록은 JDBC 한 문장(insert ignore)으로 남겨 JPA 엔티티로 두지 않는다.
 */
@Getter
public class ReceivedPaymentEvent {

    // 결제 서버가 이벤트마다 붙인 고유 번호
    private final String eventId;

    private final String orderId;

    private final String paymentStatus;

    // 결제 서버가 이 이벤트를 만든 때
    private final Instant occurredAt;

    // 이 이벤트가 실려 온 파티션과 그 안의 위치
    private final int partitionNo;

    private final long offsetNo;

    private final Instant receivedAt;

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
