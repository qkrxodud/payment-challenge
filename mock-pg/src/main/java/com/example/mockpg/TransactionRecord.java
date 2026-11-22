package com.example.mockpg;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 12장: 거래 조회(/v1/transactions)가 돌려주는 거래 한 건. 토스페이먼츠 문서: 결제 승인, 취소, 부분 취소가 이루어질 때마다 거래가 하나씩 생기고
 * 거래마다 transactionKey 가 다르다. 문서에서 확인하지 못해 가정한 것:
 * - 거래의 status 는 그 거래가 끝난 뒤의 결제 상태다(승인 거래는 DONE, 취소 거래는 취소 뒤 잔액이 남으면 PARTIAL_CANCELED, 없으면 CANCELED)
 * - 취소 거래의 amount 는 그 취소 금액이다(문서는 "결제 금액"이라고만 적는다)
 * - 거절(ABORTED)·만료(EXPIRED)는 거래로 나오지 않는다(문서가 승인·취소·부분 취소만 든다)
 */
@Getter
public class TransactionRecord {

    static final Comparator<TransactionRecord> BY_TIME = Comparator.comparing(TransactionRecord::getTransactionAt)
            .thenComparing(TransactionRecord::getTransactionKey);

    private final String transactionKey;
    private final String paymentKey;
    private final String orderId;
    private final String status;
    private final OffsetDateTime transactionAt;
    private final BigDecimal amount;

    @Builder(access = AccessLevel.PRIVATE)
    private TransactionRecord(String transactionKey, String paymentKey, String orderId, String status,
                              OffsetDateTime transactionAt, BigDecimal amount) {
        this.transactionKey = transactionKey;
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.status = status;
        this.transactionAt = transactionAt;
        this.amount = amount;
    }

    // 결제 하나의 거래들: 승인 한 건과 취소마다 한 건. 승인이 없던 결제(거절·만료)는 거래가 없다
    public static List<TransactionRecord> of(PaymentRecord payment) {
        if (payment.hasApproval()) {
            return approvalAndCancels(payment);
        }
        return List.of();
    }

    private static List<TransactionRecord> approvalAndCancels(PaymentRecord payment) {
        List<TransactionRecord> transactions = new ArrayList<>();
        transactions.add(TransactionRecord.builder()
                .transactionKey(payment.getTransactionKey())
                .paymentKey(payment.getPaymentKey())
                .orderId(payment.getOrderId())
                .status(PaymentState.DONE.name())
                .transactionAt(payment.getTransactionAt())
                .amount(payment.getTotalAmount())
                .build());
        BigDecimal balance = payment.getTotalAmount();
        for (CancelRecord cancel : payment.getCancels()) {
            balance = balance.subtract(cancel.getCancelAmount());
            transactions.add(TransactionRecord.builder()
                    .transactionKey(cancel.getTransactionKey())
                    .paymentKey(payment.getPaymentKey())
                    .orderId(payment.getOrderId())
                    .status(stateAfterCancel(balance).name())
                    .transactionAt(cancel.getCanceledAt())
                    .amount(cancel.getCancelAmount())
                    .build());
        }
        return transactions;
    }

    private static PaymentState stateAfterCancel(BigDecimal balance) {
        if (balance.signum() == 0) {
            return PaymentState.CANCELED;
        }
        return PaymentState.PARTIAL_CANCELED;
    }
}
