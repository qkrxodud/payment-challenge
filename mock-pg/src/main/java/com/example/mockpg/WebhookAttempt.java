package com.example.mockpg;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 6장: 웹훅 전송 한 번. 상점 서버가 준 상태 코드(시간 안에 답이 없으면 0)와 답을 받기까지 걸린 시간 */
@Getter
public class WebhookAttempt {

    static final int NO_ANSWER = 0;

    private final int statusCode;
    private final long elapsedMillis;

    @Builder(access = AccessLevel.PRIVATE)
    private WebhookAttempt(int statusCode, long elapsedMillis) {
        this.statusCode = statusCode;
        this.elapsedMillis = elapsedMillis;
    }

    public static WebhookAttempt of(int statusCode, long elapsedMillis) {
        return WebhookAttempt.builder()
                .statusCode(statusCode)
                .elapsedMillis(elapsedMillis)
                .build();
    }

    // 토스페이먼츠: 200 으로 답해야 성공이다
    public boolean isOk() {
        return statusCode == 200;
    }
}
