package com.example.mockpg;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 가짜 PG가 기억하는 주문 하나의 결제. 바꿀 수 없는 값이라 상태가 바뀌면 새 기록으로 갈아 끼운다.
 * 5장부터 취소할 수 있는 금액(잔액)과 취소 이력을 함께 둔다.
 * 12장부터 승인 거래의 키(transactionKey)와 처리한 때를 둔다 — 거래 조회(/v1/transactions)가 승인·취소를 거래 한 건씩 돌려준다
 */
@Getter
public class PaymentRecord {

    private final String paymentKey;
    private final String orderId;
    private final BigDecimal totalAmount;
    private final PaymentState state;
    private final BigDecimal balanceAmount;
    private final List<CancelRecord> cancels;
    // 12장: 이 결제를 처리한 거래(승인·거절·만료)의 키와 처리한 때
    private final String transactionKey;
    private final OffsetDateTime transactionAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentRecord(String paymentKey, String orderId, BigDecimal totalAmount, PaymentState state,
                          BigDecimal balanceAmount, List<CancelRecord> cancels, String transactionKey,
                          OffsetDateTime transactionAt) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.state = state;
        this.balanceAmount = balanceAmount;
        this.cancels = List.copyOf(cancels);
        this.transactionKey = transactionKey;
        this.transactionAt = transactionAt;
    }

    public static PaymentRecord of(String paymentKey, String orderId, BigDecimal totalAmount, PaymentState state) {
        return PaymentRecord.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(totalAmount)
                .state(state)
                .balanceAmount(totalAmount)
                .cancels(List.of())
                .transactionKey("tx-" + UUID.randomUUID())
                .transactionAt(OffsetDateTime.now())
                .build();
    }

    // 12장: 승인이 있었던 결제인가(승인, 부분 취소, 취소). 거래 조회에 승인 거래가 한 건 나온다
    public boolean hasApproval() {
        return state.hasApproval();
    }

    // 취소 한 건을 더한 새 기록. 잔액이 0 이 되면 취소, 남으면 부분 취소 상태가 된다
    public PaymentRecord withCancel(CancelRecord cancel) {
        BigDecimal balance = balanceAmount.subtract(cancel.getCancelAmount());
        List<CancelRecord> next = new ArrayList<>(cancels);
        next.add(cancel);
        return PaymentRecord.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(totalAmount)
                .state(balance.signum() == 0 ? PaymentState.CANCELED : PaymentState.PARTIAL_CANCELED)
                .balanceAmount(balance)
                .cancels(next)
                .transactionKey(transactionKey)
                .transactionAt(transactionAt)
                .build();
    }

    public CancelRecord lastCancel() {
        return cancels.get(cancels.size() - 1);
    }
}
