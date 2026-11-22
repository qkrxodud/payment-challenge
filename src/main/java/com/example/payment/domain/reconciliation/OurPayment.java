package com.example.payment.domain.reconciliation;

import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 12장 결정 1: 대사가 읽는 우리 결제 한 건 — 결제 키, 주문번호, 승인 금액, 상태, 끝난 취소의 합. 결제를 바꾸지 않고 읽기만 한다
 */
@Getter
public class OurPayment {

    private final String paymentKey;
    private final String orderId;
    private final BigDecimal amount;
    private final PaymentStatus status;
    private final BigDecimal canceledAmount;

    @Builder(access = AccessLevel.PRIVATE)
    private OurPayment(String paymentKey, String orderId, BigDecimal amount, PaymentStatus status, BigDecimal canceledAmount) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.canceledAmount = canceledAmount;
    }

    public static OurPayment of(String paymentKey, String orderId, BigDecimal amount, PaymentStatus status,
                                BigDecimal canceledAmount) {
        return OurPayment.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .amount(amount)
                .status(status)
                .canceledAmount(canceledAmount)
                .build();
    }

    public BigDecimal balance() {
        return amount.subtract(canceledAmount);
    }

    public SettlementStage stage() {
        if (status.isAwaitingApprovalResult()) {
            return SettlementStage.AWAITING;
        }
        if (status.holdsApproval()) {
            return approvedStage();
        }
        if (status.isCanceledAfterApproval()) {
            return SettlementStage.CANCELED;
        }
        return SettlementStage.NO_APPROVAL;
    }

    private SettlementStage approvedStage() {
        if (canceledAmount.signum() > 0) {
            return SettlementStage.PARTIALLY_CANCELED;
        }
        return SettlementStage.APPROVED;
    }
}
