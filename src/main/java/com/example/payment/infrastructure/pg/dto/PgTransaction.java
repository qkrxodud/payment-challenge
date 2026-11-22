package com.example.payment.infrastructure.pg.dto;

import java.math.BigDecimal;

// 12장: 카드사 거래 조회 응답의 Transaction 객체에서 대사가 읽는 필드만 둔다. 처리한 때는 오프셋이 붙은 ISO 8601 이다
public record PgTransaction(String transactionKey, String paymentKey, String orderId, String status, String transactionAt,
                            BigDecimal amount) {
}
