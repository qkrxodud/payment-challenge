package com.example.payment.domain.payment;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 카드사에 보낸 취소 요청의 답(5장): 취소함(카드사 취소 한 건 포함) 또는 거절함(거절 사유 코드 포함) */
@Getter
public class CancelOutcome {

    private final GatewayCancel canceled;
    private final String rejectCode;

    @Builder(access = AccessLevel.PRIVATE)
    private CancelOutcome(GatewayCancel canceled, String rejectCode) {
        this.canceled = canceled;
        this.rejectCode = rejectCode;
    }

    public static CancelOutcome canceled(GatewayCancel canceled) {
        return CancelOutcome.builder()
                .canceled(canceled)
                .build();
    }

    public static CancelOutcome rejected(String rejectCode) {
        return CancelOutcome.builder()
                .rejectCode(rejectCode)
                .build();
    }

    public boolean isCanceled() {
        return Objects.nonNull(canceled);
    }
}
