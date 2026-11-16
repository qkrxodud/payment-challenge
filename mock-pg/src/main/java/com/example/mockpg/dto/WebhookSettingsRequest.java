package com.example.mockpg.dto;

import com.example.mockpg.WebhookSettings;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

// 6장: retryIntervalMillis 를 빼면 토스페이먼츠 재전송 간격(1, 4, 16, 64, 256, 1024, 4096분)을 쓴다
public record WebhookSettingsRequest(String targetUrl, boolean held, boolean duplicated, long delayMillis,
                                     List<Long> retryIntervalMillis) {

    public WebhookSettings toSettings() {
        List<Duration> intervals = Objects.isNull(retryIntervalMillis)
                ? null
                : retryIntervalMillis.stream().map(Duration::ofMillis).toList();
        return WebhookSettings.sendTo(targetUrl, intervals)
                .holding(held)
                .duplicating(duplicated)
                .delayedBy(Duration.ofMillis(delayMillis));
    }
}
