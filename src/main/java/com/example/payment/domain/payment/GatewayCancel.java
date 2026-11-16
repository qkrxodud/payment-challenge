package com.example.payment.domain.payment;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 카드사에 남은 취소 한 건(5장): 카드사가 준 취소 건 키와 취소 금액. 6장부터 취소 사유도 읽는다(카드사에서만 일어난 취소를 기록할 때 쓴다) */
@Getter
public class GatewayCancel {

    private final String transactionKey;
    private final BigDecimal amount;
    private final String reason;

    @Builder(access = AccessLevel.PRIVATE)
    private GatewayCancel(String transactionKey, BigDecimal amount, String reason) {
        this.transactionKey = transactionKey;
        this.amount = amount;
        this.reason = reason;
    }

    public static GatewayCancel of(String transactionKey, BigDecimal amount) {
        return of(transactionKey, amount, null);
    }

    public static GatewayCancel of(String transactionKey, BigDecimal amount, String reason) {
        return GatewayCancel.builder()
                .transactionKey(transactionKey)
                .amount(amount)
                .reason(reason)
                .build();
    }
}
