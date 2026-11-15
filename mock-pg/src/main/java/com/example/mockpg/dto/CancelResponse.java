package com.example.mockpg.dto;

import com.example.mockpg.CancelRecord;
import java.math.BigDecimal;

/** Payment 객체의 cancels 배열 한 칸(5장). 토스페이먼츠 문서의 필드 가운데 결제 서버가 읽는 것만 둔다 */
public record CancelResponse(String transactionKey, BigDecimal cancelAmount, String cancelReason, String canceledAt,
                             String cancelStatus) {

    public static CancelResponse from(CancelRecord record) {
        return new CancelResponse(record.getTransactionKey(), record.getCancelAmount(), record.getCancelReason(),
                record.getCanceledAt().toString(), "DONE");
    }
}
