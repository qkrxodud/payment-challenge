package com.example.payment.application.payment.dto;

import java.math.BigDecimal;
import java.util.Objects;

// 카드사에 보내기 전에 확인한 취소: 보낼 결제 키와 금액, 또는 같은 키로 이미 받은 취소의 결과(repeated)
public record CancelAttempt(String paymentKey, BigDecimal amount, CancelResult repeated) {

    public static CancelAttempt send(String paymentKey, BigDecimal amount) {
        return new CancelAttempt(paymentKey, amount, null);
    }

    public static CancelAttempt repeat(CancelResult repeated) {
        return new CancelAttempt(null, null, repeated);
    }

    public boolean isRepeated() {
        return Objects.nonNull(repeated);
    }
}
