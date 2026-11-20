package com.example.payment.application.payment.dto;

import com.example.payment.domain.payment.PaymentItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

// 7장: 카드사에 승인을 보내기 전에 남긴 인증. 보낼 주문번호와 금액, 또는 같은 결제 키로 이미 받은 승인의 지금 결과(repeated).
// 10장: 재고 서비스에 잡을 담긴 상품(items)도 함께 넘긴다
public record ConfirmAttempt(String orderId, BigDecimal amount, List<PaymentItem> items, PaymentResult repeated) {

    public static ConfirmAttempt send(String orderId, BigDecimal amount, List<PaymentItem> items) {
        return new ConfirmAttempt(orderId, amount, items, null);
    }

    public static ConfirmAttempt repeat(PaymentResult repeated) {
        return new ConfirmAttempt(null, null, List.of(), repeated);
    }

    public boolean isRepeated() {
        return Objects.nonNull(repeated);
    }
}
