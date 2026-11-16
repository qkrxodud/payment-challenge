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
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 결제 하나에 대한 취소 요청 한 건(5장). 결제(Payment)에 딸린 기록이라 결제를 거쳐서만 만들고 바꾼다.
 * 부분 취소는 결제 하나에 여러 번 있을 수 있어서, 결제 한 줄에 합계만 두지 않고 취소마다 한 줄을 남긴다.
 * 6장부터 카드사에서만 일어난 취소도 한 건으로 들여온다. 그 취소에는 고객이 보낸 Idempotency-Key 와 요청 비교값이 없다.
 */
@Entity
@Table(name = "payment_cancel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancel {

    static final String GATEWAY_ONLY_REASON = "Canceled at the card company";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    // 고객이 취소 요청에 붙여 보낸 Idempotency-Key. 결제 하나 안에서 두 번 쓸 수 없다. 카드사에서만 일어난 취소에는 없다
    @Column(name = "idempotency_key", updatable = false, length = 300)
    private String idempotencyKey;

    // 같은 키로 다시 온 요청이 처음과 같은 내용인지 비교할 값(요청 금액·사유의 SHA-256)
    @Column(name = "request_digest", updatable = false, length = 64)
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

    // 카드사에 취소를 요청하려고 기록한 때. 카드사가 처리를 마쳤을 만큼 시간이 지난 취소만 보정한다
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentCancel(Payment payment, String idempotencyKey, String requestDigest, BigDecimal amount, String reason,
                          CancelStatus status, String transactionKey, Instant requestedAt) {
        this.payment = payment;
        this.idempotencyKey = idempotencyKey;
        this.requestDigest = requestDigest;
        this.amount = amount;
        this.reason = reason;
        this.status = status;
        this.transactionKey = transactionKey;
        this.requestedAt = requestedAt;
    }

    static PaymentCancel requested(Payment payment, CancelRequest request, BigDecimal amount, Instant now) {
        return PaymentCancel.builder()
                .payment(payment)
                .idempotencyKey(request.getIdempotencyKey())
                .requestDigest(request.requestDigest())
                .amount(amount)
                .reason(request.getReason())
                .status(CancelStatus.REQUESTED)
                .requestedAt(now)
                .build();
    }

    // 6장: 카드사에서만 일어난 취소(우리가 요청하지 않은 취소)를 끝난 취소로 들여온다. 사유가 없으면 카드사 쪽 취소라고 적는다
    static PaymentCancel fromGateway(Payment payment, GatewayCancel canceled, Instant now) {
        return PaymentCancel.builder()
                .payment(payment)
                .amount(canceled.getAmount())
                .reason(Objects.requireNonNullElse(canceled.getReason(), GATEWAY_ONLY_REASON))
                .status(CancelStatus.DONE)
                .transactionKey(canceled.getTransactionKey())
                .requestedAt(now)
                .build();
    }

    void complete(String transactionKey) {
        this.status = CancelStatus.DONE;
        this.transactionKey = transactionKey;
    }

    void fail(BigDecimal balance) {
        this.status = CancelStatus.FAILED;
        this.balanceAfter = balance;
    }

    // 이미 결과를 남긴 취소는 결과 모름으로 되돌리지 않는다
    void markUnknown() {
        if (isAwaitingResult()) {
            this.status = CancelStatus.UNKNOWN;
        }
    }

    void keepBalance(BigDecimal balance) {
        this.balanceAfter = balance;
    }

    public boolean isDone() {
        return status.isDone();
    }

    public boolean isAwaitingResult() {
        return status.isAwaitingResult();
    }

    boolean isAwaitingResultBefore(Instant requestedBefore) {
        return isAwaitingResult() && requestedAt.isBefore(requestedBefore);
    }

    boolean hasAmount(BigDecimal other) {
        return amount.compareTo(other) == 0;
    }

    boolean hasTransactionKey(String other) {
        return Objects.nonNull(transactionKey) && transactionKey.equals(other);
    }

    boolean isRequestedWith(String requestedKey) {
        return Objects.equals(idempotencyKey, requestedKey);
    }

    boolean isSameRequest(CancelRequest request) {
        return requestDigest.equals(request.requestDigest());
    }
}
