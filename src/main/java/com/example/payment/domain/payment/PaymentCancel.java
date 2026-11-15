package com.example.payment.domain.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 하나에 대한 취소 요청 한 건(5장). 결제(Payment)에 딸린 기록이라 결제를 거쳐서만 만들고 바꾼다.
 * 부분 취소는 결제 하나에 여러 번 있을 수 있어서, 결제 한 줄에 합계만 두지 않고 취소마다 한 줄을 남긴다.
 */
@Entity
@Table(name = "payment_cancel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    // 고객이 취소 요청에 붙여 보낸 Idempotency-Key
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 300)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, updatable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CancelStatus status;

    // 카드사가 취소 건마다 주는 키(토스페이먼츠 cancels[].transactionKey)
    @Column(name = "transaction_key", length = 200)
    private String transactionKey;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentCancel(Payment payment, String idempotencyKey, BigDecimal amount, String reason, CancelStatus status,
                          String transactionKey) {
        this.payment = payment;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.transactionKey = transactionKey;
    }

    static PaymentCancel done(Payment payment, String idempotencyKey, BigDecimal amount, String reason, String transactionKey) {
        return PaymentCancel.builder()
                .payment(payment)
                .idempotencyKey(idempotencyKey)
                .amount(amount)
                .reason(reason)
                .status(CancelStatus.DONE)
                .transactionKey(transactionKey)
                .build();
    }

    public boolean isDone() {
        return status.isDone();
    }

    boolean isRequestedWith(String requestedKey) {
        return idempotencyKey.equals(requestedKey);
    }
}
