package com.example.payment.application.payment.dto;

import java.math.BigDecimal;
import java.util.Objects;

// 7장: 카드사에 승인을 보내기 전에 남긴 인증. 보낼 주문번호와 금액, 또는 같은 결제 키로 이미 받은 승인의 지금 결과(repeated)
public record ConfirmAttempt(String orderId, BigDecimal amount, PaymentResult repeated) {

    public static ConfirmAttempt send(String orderId, BigDecimal amount) {
        return new ConfirmAttempt(orderId, amount, null);
    }

    public static ConfirmAttempt repeat(PaymentResult repeated) {
        return new ConfirmAttempt(null, null, repeated);
    }

    public boolean isRepeated() {
        return Objects.nonNull(repeated);
    }
}
