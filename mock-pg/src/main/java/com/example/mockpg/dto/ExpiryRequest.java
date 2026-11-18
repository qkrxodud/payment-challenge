package com.example.mockpg.dto;

import java.math.BigDecimal;

// 8장: 카드사 쪽에서 만료시킬 결제
public record ExpiryRequest(String paymentKey, BigDecimal amount) {
}
