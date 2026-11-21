package com.example.payment;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 11장: 실패 모음에 남은 메시지 한 건 — 원래 키·본문과 실패 원인(예외 종류 이름·문장). 원인을 남기지 않은 모음이면 원인은 비어 있다 */
public class DeadLetter {

    private final String key;
    private final String value;
    private final String cause;
    private final long storedAtMillis;
    private final long originalAtMillis;

    private DeadLetter(String key, String value, String cause, long storedAtMillis, long originalAtMillis) {
        this.key = key;
        this.value = value;
        this.cause = cause;
        this.storedAtMillis = storedAtMillis;
        this.originalAtMillis = originalAtMillis;
    }

    public static DeadLetter of(String key, String value, String cause, long storedAtMillis, long originalAtMillis) {
        return new DeadLetter(key, value, cause, storedAtMillis, originalAtMillis);
    }

    // Spring Kafka 실패 모음(DeadLetterPublishingRecoverer)이 붙이는 머리에서 원인을 읽는다
    public static DeadLetter fromHeaders(String key, String value, Map<String, String> headers, long storedAtMillis) {
        String cause = Stream.of("kafka_dlt-exception-cause-fqcn", "kafka_dlt-exception-fqcn", "kafka_dlt-exception-message")
                .map(name -> headers.getOrDefault(name, ""))
                .filter(Predicate.not(String::isBlank))
                .collect(Collectors.joining(" / "));
        String originalAt = headers.get("kafka_dlt-original-timestamp");
        long original = Objects.isNull(originalAt) ? storedAtMillis : Long.parseLong(originalAt);
        return new DeadLetter(key, value, cause, storedAtMillis, original);
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }

    public String cause() {
        return cause;
    }

    // 원래 메시지가 토픽에 실린 때부터 실패 모음에 남은 때까지(밀리초)
    public long heldMillis() {
        return storedAtMillis - originalAtMillis;
    }
}
