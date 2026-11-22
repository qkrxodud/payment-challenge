package com.example.payment.domain.reconciliation;

import com.example.payment.domain.payment.GatewayStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 12장 결정 1: 카드사 거래 내역의 거래 한 건(토스페이먼츠 Transaction 객체). 승인·취소·부분 취소마다 한 건이고 거래 키(transactionKey)가 다르다.
 * 상태는 그 거래가 끝난 뒤의 결제 상태, 금액은 승인 거래면 승인 금액·취소 거래면 취소 금액으로 읽는다(문서에서 확인하지 못한 가정 — docs/ch12/environment.md)
 */
@Getter
public class GatewayTransaction {

    private final String transactionKey;
    private final String paymentKey;
    private final String orderId;
    private final GatewayStatus status;
    private final Instant transactionAt;
    private final BigDecimal amount;

    @Builder(access = AccessLevel.PRIVATE)
    private GatewayTransaction(String transactionKey, String paymentKey, String orderId, GatewayStatus status,
                               Instant transactionAt, BigDecimal amount) {
        this.transactionKey = transactionKey;
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.status = status;
        this.transactionAt = transactionAt;
        this.amount = amount;
    }

    public static GatewayTransaction of(String transactionKey, String paymentKey, String orderId, GatewayStatus status,
                                        Instant transactionAt, BigDecimal amount) {
        return GatewayTransaction.builder()
                .transactionKey(transactionKey)
                .paymentKey(paymentKey)
                .orderId(orderId)
                .status(status)
                .transactionAt(transactionAt)
                .amount(amount)
                .build();
    }

    // 승인 거래인가(거래 뒤 상태가 승인). 취소 거래는 부분 취소·취소 상태다
    public boolean isApproval() {
        return status == GatewayStatus.DONE;
    }
}
