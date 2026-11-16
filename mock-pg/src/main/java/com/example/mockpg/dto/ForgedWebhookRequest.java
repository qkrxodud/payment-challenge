package com.example.mockpg.dto;

import java.math.BigDecimal;

// 6장: 카드사 기록과 다른 상태를 담아 보낼 위조 알림
public record ForgedWebhookRequest(String orderId, String status, BigDecimal amount) {
}
