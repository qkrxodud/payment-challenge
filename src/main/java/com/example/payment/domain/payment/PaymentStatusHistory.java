package com.example.payment.domain.payment;

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
 * 12장 결정 3: 결제 상태가 바뀐 이력 한 줄. 상태를 바꾼 그 트랜잭션에서 남긴다 — 상태가 커밋되면 이 줄도 남고, 되돌려지면 함께 남지 않는다.
 * 칸: 이전 상태·다음 상태(어디서 어디로), 행위(허용 전이 표의 어느 칸으로 — 같은 두 상태 사이에 행위는 하나지만 표를 바꿔도 읽을 수 있게 남긴다),
 * 계기(승인 요청·보정·알림·취소·대사 가운데 어느 길로), 바뀐 때(그 기록을 남긴 서버 시각). 고친 적이 없는 줄이라 바꾸는 메서드가 없다.
 */
@Entity
@Table(name = "payment_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false, length = 30)
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 30)
    private PaymentStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 30)
    private PaymentAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by", nullable = false, updatable = false, length = 30)
    private StatusTrigger changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentStatusHistory(Long paymentId, PaymentStatus fromStatus, PaymentStatus toStatus, PaymentAction action,
                                 StatusTrigger changedBy, Instant changedAt) {
        this.paymentId = paymentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.action = action;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public static PaymentStatusHistory of(Long paymentId, StatusTransition transition, StatusTrigger changedBy, Instant changedAt) {
        return PaymentStatusHistory.builder()
                .paymentId(paymentId)
                .fromStatus(transition.getFrom())
                .toStatus(transition.getTo())
                .action(transition.getAction())
                .changedBy(changedBy)
                .changedAt(changedAt)
                .build();
    }
}
