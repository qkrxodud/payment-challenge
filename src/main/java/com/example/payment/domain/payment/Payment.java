package com.example.payment.domain.payment;

import com.example.payment.global.error.exception.AmountMismatchException;
import com.example.payment.global.error.exception.DuplicateOrderException;
import com.example.payment.global.error.exception.InvalidAmountException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
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

    public void authenticate(String paymentKey) {
        changeStatus(PaymentAction.AUTHENTICATE);
        this.paymentKey = paymentKey;
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

    public void expire() {
        changeStatus(PaymentAction.EXPIRE);
    }

    public void capture() {
        changeStatus(PaymentAction.CAPTURE);
    }

    public void cancel() {
        changeStatus(PaymentAction.CANCEL);
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

    // 이 결제 키로 승인을 요청해 카드사 답까지 기록한 결제인가. 답을 기록하기 전(AUTHENTICATED)은 아니다
    public boolean isConfirmedWith(String requestedKey) {
        return Objects.nonNull(paymentKey)
                && paymentKey.equals(requestedKey)
                && status != PaymentStatus.AUTHENTICATED;
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
