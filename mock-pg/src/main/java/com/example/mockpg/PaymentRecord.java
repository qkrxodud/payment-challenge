package com.example.mockpg;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 가짜 PG가 기억하는 주문 하나의 결제. 바꿀 수 없는 값이라 상태가 바뀌면 새 기록으로 갈아 끼운다 */
@Getter
public class PaymentRecord {

    private final String paymentKey;
    private final String orderId;
    private final BigDecimal totalAmount;
    private final PaymentState state;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentRecord(String paymentKey, String orderId, BigDecimal totalAmount, PaymentState state) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.state = state;
    }

    public static PaymentRecord of(String paymentKey, String orderId, BigDecimal totalAmount, PaymentState state) {
        return PaymentRecord.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .totalAmount(totalAmount)
                .state(state)
                .build();
    }

    public PaymentRecord canceled() {
        return of(paymentKey, orderId, totalAmount, PaymentState.CANCELED);
    }
}
