package com.example.mockpg.dto;

import java.math.BigDecimal;

// 6장: 가맹점 관리 화면 취소. 금액이 없으면 남은 금액을 모두 취소한다
public record ConsoleCancelRequest(BigDecimal cancelAmount, String cancelReason) {
}
