package com.example.mockpg.dto;

import java.math.BigDecimal;

public record ConfirmResponse(String paymentKey, String orderId, String status, BigDecimal totalAmount) {

    public static ConfirmResponse done(ConfirmRequest request) {
        return new ConfirmResponse(request.paymentKey(), request.orderId(), "DONE", request.amount());
    }
}
