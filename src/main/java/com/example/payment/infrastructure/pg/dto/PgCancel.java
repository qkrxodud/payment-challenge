package com.example.payment.infrastructure.pg.dto;

import java.math.BigDecimal;

// 카드사 Payment 객체의 cancels 배열 한 칸에서 읽는 필드만 둔다
public record PgCancel(String transactionKey, BigDecimal cancelAmount, String cancelStatus) {

    // 토스페이먼츠: cancelStatus 가 DONE 이면 결제가 성공적으로 취소된 상태
    public boolean isDone() {
        return "DONE".equals(cancelStatus);
    }
}
