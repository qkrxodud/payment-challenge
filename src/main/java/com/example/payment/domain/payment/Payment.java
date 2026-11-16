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
import java.util.function.Predicate;
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

    // 카드사에서 다시 읽은 결제를 따라간다(6장 결정 2). 지금 상태에서 허용 전이 표로 갈 수 있는 방향이면 따라가고,
    // 이미 지난 상태(먼저 온 알림으로 이미 따라갔거나, 우리가 먼저 결과를 남김)면 아무것도 하지 않는다. 표에 없는 전이로 거부하지 않는다.
    // 승인 결과를 기다리는 결제는 카드사에 승인이 있었으면 승인, 거절·만료면 승인 실패로 닫는다. 카드사도 아직 처리 중이거나
    // 기록이 없으면 그대로 둔다(알림만으로 실패로 닫지 않는다 — 승인 요청이 아직 처리 중일 수 있다)
    public void follow(GatewayPayment gatewayPayment) {
        GatewayStatus gatewayStatus = gatewayPayment.getStatus();
        if (status.isAwaitingApprovalResult()) {
            followApproval(gatewayStatus);
        }
        if (status.holdsApproval() && gatewayStatus.isCanceled()) {
            cancel();
        }
    }

    private void followApproval(GatewayStatus gatewayStatus) {
        if (gatewayStatus.hasApproval()) {
            approve();
            return;
        }
        if (gatewayStatus.isDeclined()) {
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

    // 카드사에 보내기 전에 취소를 받을 수 있는지 보고, 받으면 카드사 답을 기다리는 취소로 남긴다(5장 결정 2·4).
    // 승인이 남은 결제(승인, 매입)만, 더 취소할 수 있는 금액 안에서만 받는다. 금액이 없으면 그 금액 전부를 취소한다
    public PaymentCancel requestCancel(CancelRequest request, Instant now) {
        PaymentCancel cancel = PaymentCancel.requested(this, request, checkCancel(request), now);
        cancels.add(cancel);
        return cancel;
    }

    private BigDecimal checkCancel(CancelRequest request) {
        if (status.refuses(PaymentAction.CANCEL)) {
            throw new InvalidPaymentTransitionException(status.name(), PaymentAction.CANCEL.name());
        }
        BigDecimal cancelAmount = Objects.requireNonNullElse(request.getRequestedAmount(), cancelableAmount());
        if (isNotPositive(cancelAmount)) {
            throw new InvalidAmountException(orderId);
        }
        if (cancelAmount.compareTo(cancelableAmount()) > 0) {
            throw new CancelAmountExceedsBalanceException(orderId);
        }
        return cancelAmount;
    }

    // 카드사가 취소했다. 결제 상태는 그대로 두고, 남은 금액이 0원이 되면 취소 상태로 바꾼다.
    // 그 사이 보정이 먼저 결과를 남겼으면 건드리지 않는다
    public PaymentCancel completeCancel(String idempotencyKey, GatewayCancel canceled) {
        PaymentCancel cancel = awaitingCancel(idempotencyKey);
        if (cancel.isAwaitingResult()) {
            complete(cancel, canceled.getTransactionKey());
        }
        return cancel;
    }

    // 카드사가 취소를 거절했다. 남은 금액은 그대로다
    public PaymentCancel failCancel(String idempotencyKey) {
        PaymentCancel cancel = awaitingCancel(idempotencyKey);
        if (cancel.isAwaitingResult()) {
            cancel.fail(balanceAmount());
        }
        return cancel;
    }

    // 카드사에 취소를 보냈지만 답을 받지 못했다. 취소됐을 수 있어 나중에 카드사 기록으로 맞춘다
    public void markCancelUnknown(String idempotencyKey) {
        awaitingCancel(idempotencyKey).markUnknown();
    }

    // 카드사에 남은 취소로 결과를 기다리는 취소를 닫는다(5장 결정 4). 정한 때보다 먼저 요청한 취소만 본다.
    // 우리가 아직 모르는 카드사 취소 가운데 같은 금액이 있으면 그 취소로 끝난 것으로, 없으면 카드사가 취소하지 않은 것으로 본다
    public void settleCancels(List<GatewayCancel> gatewayCancels, Instant requestedBefore) {
        List<GatewayCancel> unmatched = new ArrayList<>(gatewayCancels.stream()
                .filter(this::isUnknownTransaction)
                .toList());
        cancels.stream()
                .filter(cancel -> cancel.isAwaitingResultBefore(requestedBefore))
                .forEach(cancel -> settle(cancel, unmatched));
    }

    private void settle(PaymentCancel cancel, List<GatewayCancel> unmatched) {
        GatewayCancel matched = unmatched.stream()
                .filter(gatewayCancel -> cancel.hasAmount(gatewayCancel.getAmount()))
                .findFirst()
                .orElse(null);
        if (Objects.isNull(matched)) {
            cancel.fail(balanceAmount());
            return;
        }
        unmatched.remove(matched);
        complete(cancel, matched.getTransactionKey());
    }

    private boolean isUnknownTransaction(GatewayCancel gatewayCancel) {
        return cancels.stream().noneMatch(cancel -> cancel.hasTransactionKey(gatewayCancel.getTransactionKey()));
    }

    private void complete(PaymentCancel cancel, String transactionKey) {
        cancel.complete(transactionKey);
        cancel.keepBalance(balanceAmount());
        cancelIfNothingLeft();
    }

    private void cancelIfNothingLeft() {
        if (balanceAmount().signum() == 0) {
            cancel();
        }
    }

    // 카드사가 취소한 뒤 남은 금액: 결제 금액에서 끝난 취소를 뺀 값
    public BigDecimal balanceAmount() {
        return amount.subtract(sumOf(PaymentCancel::isDone));
    }

    // 새 취소로 받을 수 있는 금액: 남은 금액에서 카드사 답을 기다리는 취소까지 뺀 값
    private BigDecimal cancelableAmount() {
        return balanceAmount().subtract(sumOf(PaymentCancel::isAwaitingResult));
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

    // 결과를 남기려는 취소는 이 요청 앞에서 이미 기록했다. 없다면 기록이 어긋난 것이라 내부 오류(500)로 알린다
    private PaymentCancel awaitingCancel(String idempotencyKey) {
        return findCancel(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("A cancel recorded before calling the gateway is missing."));
    }

    private BigDecimal sumOf(Predicate<PaymentCancel> condition) {
        return cancels.stream()
                .filter(condition)
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
