package com.example.payment.domain.payment;

import com.example.payment.global.error.exception.AmountMismatchException;
import com.example.payment.global.error.exception.CancelAmountExceedsBalanceException;
import com.example.payment.global.error.exception.DuplicateOrderException;
import com.example.payment.global.error.exception.IdempotencyKeyReusedException;
import com.example.payment.global.error.exception.InvalidAmountException;
import com.example.payment.global.error.exception.InvalidPaymentTransitionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false, length = 64)
    private String orderId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status;

    // 결제창 인증 때 PG 가 준 키. 다시 온 승인 요청이 같은 요청인지 이것으로 가린다
    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    // 승인을 요청하려고 인증한 때(4장). 카드사가 처리를 마쳤을 만큼 시간이 지난 결제만 보정한다
    @Column(name = "authenticated_at")
    private Instant authenticatedAt;

    // 5장: 이 결제의 취소. 취소마다 한 줄을 남기고, 남은 금액은 끝난 취소의 합으로 계산한다
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    private List<PaymentCancel> cancels = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private Payment(String orderId, BigDecimal amount, PaymentStatus status) {
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
    }

    public static Payment create(String orderId, BigDecimal amount) {
        if (isNotPositive(amount)) {
            throw new InvalidAmountException(orderId);
        }
        return Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .status(PaymentStatus.READY)
                .build();
    }

    public void authenticate(String paymentKey, Instant now) {
        changeStatus(PaymentAction.AUTHENTICATE);
        this.paymentKey = paymentKey;
        this.authenticatedAt = now;
    }

    public void failAuthentication() {
        changeStatus(PaymentAction.FAIL_AUTHENTICATION);
    }

    public void approve() {
        changeStatus(PaymentAction.APPROVE);
    }

    public void failApproval() {
        changeStatus(PaymentAction.FAIL_APPROVAL);
    }

    public void markApprovalUnknown() {
        changeStatus(PaymentAction.MARK_APPROVAL_UNKNOWN);
    }

    // 카드사에 확인한 결과로 결제를 닫는다. 카드사도 아직 처리 중이면 그대로 둔다
    public void settle(PaymentInquiry inquiry) {
        if (inquiry.isApproved()) {
            approve();
            return;
        }
        if (inquiry.isNotApproved()) {
            failApproval();
        }
    }

    public void expire() {
        changeStatus(PaymentAction.EXPIRE);
    }

    public void capture() {
        changeStatus(PaymentAction.CAPTURE);
    }

    public void cancel() {
        changeStatus(PaymentAction.CANCEL);
    }

    // 같은 Idempotency-Key 로 이미 받은 취소(5장 결정 3). 키가 같은데 내용이 다르면 받지 않는다
    public Optional<PaymentCancel> findRepeatedCancel(CancelRequest request) {
        PaymentCancel repeated = findCancel(request.getIdempotencyKey()).orElse(null);
        if (Objects.isNull(repeated)) {
            return Optional.empty();
        }
        if (repeated.isSameRequest(request)) {
            return Optional.of(repeated);
        }
        throw new IdempotencyKeyReusedException(orderId);
    }

    // 카드사에 보내기 전에 취소를 받을 수 있는지 보고 취소할 금액을 정한다(5장). 금액이 없으면 남은 금액 전부를 취소한다.
    // 승인이 남은 결제(승인, 매입)만, 남은 금액 안에서만 받는다
    public BigDecimal checkCancel(CancelRequest request) {
        if (status.refuses(PaymentAction.CANCEL)) {
            throw new InvalidPaymentTransitionException(status.name(), PaymentAction.CANCEL.name());
        }
        BigDecimal cancelAmount = Objects.requireNonNullElse(request.getRequestedAmount(), balanceAmount());
        if (isNotPositive(cancelAmount)) {
            throw new InvalidAmountException(orderId);
        }
        if (cancelAmount.compareTo(balanceAmount()) > 0) {
            throw new CancelAmountExceedsBalanceException(orderId);
        }
        return cancelAmount;
    }

    // 카드사가 취소한 결과를 취소 한 건으로 남긴다(5장). 결제 상태는 그대로 두고, 남은 금액이 0원이 되면 취소 상태로 바꾼다.
    // 그때의 남은 금액을 취소 기록에도 남겨 같은 요청이 다시 오면 처음과 같은 답을 준다
    public PaymentCancel recordCancel(CancelRequest request, BigDecimal cancelAmount, GatewayCancel canceled) {
        PaymentCancel cancel = PaymentCancel.done(this, request, cancelAmount, canceled.getTransactionKey());
        cancels.add(cancel);
        cancel.keepBalance(balanceAmount());
        cancelIfNothingLeft();
        return cancel;
    }

    private void cancelIfNothingLeft() {
        if (balanceAmount().signum() == 0) {
            cancel();
        }
    }

    // 더 취소할 수 있는 금액: 결제 금액에서 끝난 취소를 뺀 값
    public BigDecimal balanceAmount() {
        return amount.subtract(canceledAmount());
    }

    // 받은 순서대로의 취소. 바꿀 수 없는 사본이다
    public List<PaymentCancel> getCancels() {
        return List.copyOf(cancels);
    }

    public Optional<PaymentCancel> findCancel(String idempotencyKey) {
        return cancels.stream()
                .filter(cancel -> cancel.isRequestedWith(idempotencyKey))
                .findFirst();
    }

    private BigDecimal canceledAmount() {
        return cancels.stream()
                .filter(PaymentCancel::isDone)
                .map(PaymentCancel::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // 금액은 값만 비교한다. DB 에서 읽은 15000.00 과 요청의 15000 은 같은 금액이다
    public void verifyAmount(BigDecimal requested) {
        if (Objects.isNull(requested)) {
            throw new InvalidAmountException(orderId);
        }
        if (isDifferentAmount(requested)) {
            throw new AmountMismatchException(orderId);
        }
    }

    // 같은 주문번호로 다시 온 준비 요청. 금액 자체가 틀리면 새 결제를 만들 때와 같이 거부한다
    public void verifyPreparedAmount(BigDecimal requested) {
        if (isNotPositive(requested)) {
            throw new InvalidAmountException(orderId);
        }
        if (isDifferentAmount(requested)) {
            throw new DuplicateOrderException(orderId);
        }
    }

    // 이 결제 키로 승인을 요청해 결과를 남긴 결제인가. 답을 받지 못한 결과 모름(APPROVAL_UNKNOWN)도 남긴 결과로 본다.
    // 답을 기록하기 전(AUTHENTICATED)은 아니다
    public boolean isConfirmedWith(String requestedKey) {
        return Objects.nonNull(paymentKey)
                && paymentKey.equals(requestedKey)
                && status != PaymentStatus.AUTHENTICATED;
    }

    public boolean isAwaitingApprovalResult() {
        return status.isAwaitingApprovalResult();
    }

    private boolean isDifferentAmount(BigDecimal requested) {
        return amount.compareTo(requested) != 0;
    }

    private static boolean isNotPositive(BigDecimal amount) {
        return Objects.isNull(amount) || amount.signum() <= 0;
    }

    private void changeStatus(PaymentAction action) {
        this.status = status.next(action);
    }
}
