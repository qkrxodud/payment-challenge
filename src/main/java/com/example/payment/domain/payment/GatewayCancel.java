package com.example.payment.domain.payment;

import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 카드사에 남은 취소 한 건(5장): 카드사가 준 취소 건 키와 취소 금액 */
@Getter
public class GatewayCancel {

    private final String transactionKey;
    private final BigDecimal amount;

    @Builder(access = AccessLevel.PRIVATE)
    private GatewayCancel(String transactionKey, BigDecimal amount) {
        this.transactionKey = transactionKey;
        this.amount = amount;
    }

    public static GatewayCancel of(String transactionKey, BigDecimal amount) {
        return GatewayCancel.builder()
                .transactionKey(transactionKey)
                .amount(amount)
                .build();
    }
}
