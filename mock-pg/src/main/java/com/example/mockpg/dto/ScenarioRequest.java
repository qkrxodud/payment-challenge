package com.example.mockpg.dto;

import com.example.mockpg.Outcome;
import java.time.Duration;
import java.util.Objects;

// followUpDelayMillis 는 4장부터. 빼고 보내면 조회·취소에 바로 답한다
public record ScenarioRequest(long delayMillis, Outcome outcome, Long followUpDelayMillis) {

    public Duration followUpDelay() {
        if (Objects.isNull(followUpDelayMillis)) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(followUpDelayMillis);
    }
}
