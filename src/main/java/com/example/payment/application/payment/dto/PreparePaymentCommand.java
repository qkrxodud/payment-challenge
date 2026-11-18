package com.example.payment.application.payment.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

// 8장부터 상품(items)·쿠폰·사용 포인트를 함께 받을 수 있다. 상품이 없으면 7장까지처럼 주문번호와 금액만으로 준비한다
public record PreparePaymentCommand(String orderId, BigDecimal amount, String memberId, List<OrderLine> items,
                                    Long couponId, BigDecimal usePoint) {

    public PreparePaymentCommand(String orderId, BigDecimal amount) {
        this(orderId, amount, null, null, null, null);
    }

    public boolean hasItems() {
        return Objects.nonNull(items) && items.size() > 0;
    }
}
