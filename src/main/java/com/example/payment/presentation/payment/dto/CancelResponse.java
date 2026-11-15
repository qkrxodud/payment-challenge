package com.example.payment.presentation.payment.dto;

import com.example.payment.application.payment.dto.CancelResult;
import java.math.BigDecimal;

public record CancelResponse(String orderId, BigDecimal cancelAmount, String cancelStatus, BigDecimal balanceAmount) {

    public static CancelResponse from(CancelResult result) {
        return new CancelResponse(result.orderId(), result.cancelAmount(), result.cancelStatus().name(), result.balanceAmount());
    }
}
