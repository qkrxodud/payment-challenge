package com.example.mockpg;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 6장: 웹훅 한 건과 그 전송 기록. 처음 전송과 재전송마다 상점 서버가 준 상태 코드(답이 없으면 0)와 걸린 시간을 남긴다.
 * generation 은 이 알림을 만든 때의 Mock PG 세대다. 시험이 Mock PG 를 되돌리면(reset) 앞 세대의 재전송은 멈춘다.
 */
@Getter
public class WebhookDelivery {

    private final long generation;
    private final String orderId;
    private final String status;
    private final String body;
    @Getter(AccessLevel.NONE)
    private final List<WebhookAttempt> attempts = new CopyOnWriteArrayList<>();
    @Getter(AccessLevel.NONE)
    private final AtomicBoolean settled = new AtomicBoolean(false);

    @Builder(access = AccessLevel.PRIVATE)
    private WebhookDelivery(long generation, String orderId, String status, String body) {
        this.generation = generation;
        this.orderId = orderId;
        this.status = status;
        this.body = body;
    }

    public static WebhookDelivery of(long generation, String orderId, String status, String body) {
        return WebhookDelivery.builder()
                .generation(generation)
                .orderId(orderId)
                .status(status)
                .body(body)
                .build();
    }

    void record(WebhookAttempt attempt) {
        attempts.add(attempt);
    }

    // 200 을 받았거나 재전송을 다 써서 더 보내지 않는다
    void settle() {
        settled.set(true);
    }

    public boolean isSettled() {
        return settled.get();
    }

    public List<WebhookAttempt> getAttempts() {
        return List.copyOf(attempts);
    }

    public boolean isDelivered() {
        return attempts.stream().anyMatch(WebhookAttempt::isOk);
    }
}
