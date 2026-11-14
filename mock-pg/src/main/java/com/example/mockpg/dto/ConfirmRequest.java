package com.example.mockpg.dto;

import java.math.BigDecimal;

public record ConfirmRequest(String paymentKey, String orderId, BigDecimal amount) {
}
