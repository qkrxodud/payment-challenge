package com.example.mockpg;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 가짜 PG가 남긴 취소 한 건(5장). transactionKey 는 취소 건마다 새로 만든다 */
@Getter
public class CancelRecord {

    private final String transactionKey;
    private final BigDecimal cancelAmount;
    private final String cancelReason;
    private final OffsetDateTime canceledAt;

    @Builder(access = AccessLevel.PRIVATE)
    private CancelRecord(String transactionKey, BigDecimal cancelAmount, String cancelReason, OffsetDateTime canceledAt) {
        this.transactionKey = transactionKey;
        this.cancelAmount = cancelAmount;
        this.cancelReason = cancelReason;
        this.canceledAt = canceledAt;
    }

    public static CancelRecord of(BigDecimal cancelAmount, String cancelReason) {
        return CancelRecord.builder()
                .transactionKey("tx-" + UUID.randomUUID())
                .cancelAmount(cancelAmount)
                .cancelReason(cancelReason)
                .canceledAt(OffsetDateTime.now())
                .build();
    }
}
