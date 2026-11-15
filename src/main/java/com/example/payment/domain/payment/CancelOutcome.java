package com.example.payment.domain.payment;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 카드사에 보낸 취소 요청의 답(5장): 취소함(카드사 취소 한 건 포함), 거절함(거절 사유 코드 포함),
 * 또는 같은 멱등키의 앞 요청을 카드사가 아직 처리하는 중이라 답이 아직 없음
 */
@Getter
public class CancelOutcome {

    private final GatewayCancel canceled;
    private final String rejectCode;
    private final boolean inProgress;

    @Builder(access = AccessLevel.PRIVATE)
    private CancelOutcome(GatewayCancel canceled, String rejectCode, boolean inProgress) {
        this.canceled = canceled;
        this.rejectCode = rejectCode;
        this.inProgress = inProgress;
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

    public static CancelOutcome inProgress() {
        return CancelOutcome.builder()
                .inProgress(true)
                .build();
    }

    public boolean isCanceled() {
        return Objects.nonNull(canceled);
    }
}
