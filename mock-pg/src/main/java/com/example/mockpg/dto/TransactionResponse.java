package com.example.mockpg.dto;

import com.example.mockpg.TransactionRecord;
import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * 12장: 거래 조회 응답의 Transaction 객체 한 칸. 토스페이먼츠 문서의 필드(mId, transactionKey, paymentKey, orderId, method, customerKey,
 * useEscrow, receiptUrl, status, transactionAt, currency, amount) 가운데 결제 서버가 대사에 쓰는 것과 method·currency 만 둔다
 */
public record TransactionResponse(String transactionKey, String paymentKey, String orderId, String method, String status,
                                  String transactionAt, String currency, BigDecimal amount) {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    // 처리한 때는 한국 시각 오프셋(+09:00)을 붙인 ISO 8601 로 낸다(문서 예와 같은 꼴)
    public static TransactionResponse from(TransactionRecord record) {
        return new TransactionResponse(record.getTransactionKey(), record.getPaymentKey(), record.getOrderId(), "카드",
                record.getStatus(), record.getTransactionAt().atZoneSameInstant(KOREA).toOffsetDateTime().toString(), "KRW",
                record.getAmount());
    }
}
