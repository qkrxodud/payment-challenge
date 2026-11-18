package com.example.payment.presentation.payment.dto;

import com.example.payment.application.payment.dto.OrderLine;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

// 8장부터 memberId·items·couponId·usePoint 를 더 받을 수 있다(모두 고를 수 있음). items 가 없으면 7장까지와 같다
public record PreparePaymentRequest(String orderId, BigDecimal amount, String memberId, List<OrderLineRequest> items,
                                    Long couponId, BigDecimal usePoint) {

    public PreparePaymentCommand toCommand() {
        return new PreparePaymentCommand(orderId, amount, memberId, lines(), couponId, usePoint);
    }

    private List<OrderLine> lines() {
        if (Objects.isNull(items)) {
            return List.of();
        }
        return items.stream()
                .map(item -> new OrderLine(item.productId(), item.quantity()))
                .toList();
    }
}
