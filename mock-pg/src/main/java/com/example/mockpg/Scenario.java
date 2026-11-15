package com.example.mockpg;

import java.time.Duration;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 승인 요청을 받았을 때 얼마나 기다렸다가 어떤 결과를 줄지, 그리고 결제 조회·취소에 얼마나 늦게 답할지.
 * 바꿀 수 없는 값이라 여러 요청이 함께 읽어도 된다.
 */
@Getter
public class Scenario {

    private final Duration delay;
    private final Outcome outcome;
    // 4장부터: 승인 뒤에 오는 조회·취소 요청의 응답 지연
    private final Duration followUpDelay;

    @Builder(access = AccessLevel.PRIVATE)
    private Scenario(Duration delay, Outcome outcome, Duration followUpDelay) {
        this.delay = delay;
        this.outcome = outcome;
        this.followUpDelay = followUpDelay;
    }

    public static Scenario immediateApproval() {
        return of(Duration.ZERO, Outcome.APPROVE);
    }

    public static Scenario of(Duration delay, Outcome outcome) {
        return of(delay, outcome, Duration.ZERO);
    }

    public static Scenario of(Duration delay, Outcome outcome, Duration followUpDelay) {
        return Scenario.builder()
                .delay(delay)
                .outcome(outcome)
                .followUpDelay(followUpDelay)
                .build();
    }
}
