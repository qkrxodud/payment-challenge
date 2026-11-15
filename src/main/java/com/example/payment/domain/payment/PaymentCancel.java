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

    // 고객이 취소 요청에 붙여 보낸 Idempotency-Key. 결제 하나 안에서 두 번 쓸 수 없다
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 300)
    private String idempotencyKey;

    // 같은 키로 다시 온 요청이 처음과 같은 내용인지 비교할 값(요청 금액·사유의 SHA-256)
    @Column(name = "request_digest", nullable = false, updatable = false, length = 64)
    private String requestDigest;

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

    // 이 취소가 끝난 뒤 남은 금액. 같은 키로 다시 온 요청에 처음과 같은 답을 주려고 남긴다
    @Column(name = "balance_after", precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentCancel(Payment payment, String idempotencyKey, String requestDigest, BigDecimal amount, String reason,
                          CancelStatus status, String transactionKey) {
        this.payment = payment;
        this.idempotencyKey = idempotencyKey;
        this.requestDigest = requestDigest;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.transactionKey = transactionKey;
    }

    static PaymentCancel done(Payment payment, CancelRequest request, BigDecimal amount, String transactionKey) {
        return PaymentCancel.builder()
                .payment(payment)
                .idempotencyKey(request.getIdempotencyKey())
                .requestDigest(request.requestDigest())
                .amount(amount)
                .reason(request.getReason())
                .status(CancelStatus.DONE)
                .transactionKey(transactionKey)
                .build();
    }

    void keepBalance(BigDecimal balance) {
        this.balanceAfter = balance;
    }

    public boolean isDone() {
        return status.isDone();
    }

    boolean isRequestedWith(String requestedKey) {
        return idempotencyKey.equals(requestedKey);
    }

    boolean isSameRequest(CancelRequest request) {
        return requestDigest.equals(request.requestDigest());
    }
}
