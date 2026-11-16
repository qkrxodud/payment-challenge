package com.example.mockpg;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 6장: 결제 상태가 바뀔 때 상점 서버로 보내는 웹훅(PAYMENT_STATUS_CHANGED)의 설정. 바꿀 수 없는 값이라 여러 스레드가 함께 읽어도 된다.
 * 토스페이먼츠 문서에서 확인한 것: 10초 안에 200 으로 답하면 성공, 아니면 최대 7회 재전송(간격 1, 4, 16, 64, 256, 1024, 4096분).
 * 시험에서는 재전송 간격을 줄여서 쓴다(retryIntervals).
 * 시험용으로만 있는 조작: 보내지 않고 쌓아 두기(held, 나중에 순서를 바꿔 보냄), 같은 알림 두 번 보내기(duplicated), 늦게 보내기(delay).
 */
@Getter
public class WebhookSettings {

    static final List<Duration> TOSS_RETRY_INTERVALS = Stream.of(1L, 4L, 16L, 64L, 256L, 1024L, 4096L)
            .map(Duration::ofMinutes)
            .toList();
    static final Duration TOSS_ANSWER_TIMEOUT = Duration.ofSeconds(10);

    // 웹훅을 받을 상점 서버 주소. 없으면 보내지 않는다
    private final String targetUrl;
    private final boolean held;
    private final boolean duplicated;
    private final Duration delay;
    private final List<Duration> retryIntervals;
    private final Duration answerTimeout;

    @Builder(access = AccessLevel.PRIVATE, toBuilder = true)
    private WebhookSettings(String targetUrl, boolean held, boolean duplicated, Duration delay, List<Duration> retryIntervals,
                            Duration answerTimeout) {
        this.targetUrl = targetUrl;
        this.held = held;
        this.duplicated = duplicated;
        this.delay = delay;
        this.retryIntervals = List.copyOf(retryIntervals);
        this.answerTimeout = answerTimeout;
    }

    public static WebhookSettings off() {
        return WebhookSettings.builder()
                .delay(Duration.ZERO)
                .retryIntervals(TOSS_RETRY_INTERVALS)
                .answerTimeout(TOSS_ANSWER_TIMEOUT)
                .build();
    }

    // 상점 서버로 보낸다. 재전송 간격을 비워 두면 토스페이먼츠 간격을 쓴다
    public static WebhookSettings sendTo(String targetUrl, List<Duration> retryIntervals) {
        return off().toBuilder()
                .targetUrl(targetUrl)
                .retryIntervals(Objects.requireNonNullElse(retryIntervals, TOSS_RETRY_INTERVALS))
                .build();
    }

    public WebhookSettings holding(boolean held) {
        return toBuilder().held(held).build();
    }

    public WebhookSettings duplicating(boolean duplicated) {
        return toBuilder().duplicated(duplicated).build();
    }

    public WebhookSettings delayedBy(Duration delay) {
        return toBuilder().delay(delay).build();
    }

    public boolean isOff() {
        return Objects.isNull(targetUrl);
    }
}
