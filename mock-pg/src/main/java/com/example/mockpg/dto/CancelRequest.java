package com.example.mockpg.dto;

import java.math.BigDecimal;

// cancelAmount 는 5장부터. 빼고 보내면 남은 잔액을 모두 취소한다(토스페이먼츠: "값이 없으면 전액 취소됩니다")
public record CancelRequest(String cancelReason, BigDecimal cancelAmount) {
}
