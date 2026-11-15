package com.example.mockpg;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 취소 요청 하나를 처리한 결과와 그 뒤의 결제 기록(5장) */
@Getter
public class CancelAttempt {

    private final CancelOutcome outcome;
    private final PaymentRecord record;

    @Builder(access = AccessLevel.PRIVATE)
    private CancelAttempt(CancelOutcome outcome, PaymentRecord record) {
        this.outcome = outcome;
        this.record = record;
    }

    public static CancelAttempt of(CancelOutcome outcome, PaymentRecord record) {
        return CancelAttempt.builder()
                .outcome(outcome)
                .record(record)
                .build();
    }
}
