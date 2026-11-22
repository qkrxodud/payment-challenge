package com.example.payment.application.payment;

import java.time.Instant;

/**
 * 12장 시험 도구: 결제 상태 이력 한 줄을 시험이 읽는 꼴(이전 상태, 다음 상태, 바꾼 행위, 계기, 바뀐 때). 이전 상태·행위·계기를 남기지 않는 구현은
 * 그 칸이 비어 있다. 이름은 운영 코드의 이름 그대로 문자열로 둔다 — 출발 구조에는 이력이 없다
 */
public class HistoryLine {

    private final String from;
    private final String to;
    private final String action;
    private final String trigger;
    private final Instant changedAt;

    private HistoryLine(String from, String to, String action, String trigger, Instant changedAt) {
        this.from = from;
        this.to = to;
        this.action = action;
        this.trigger = trigger;
        this.changedAt = changedAt;
    }

    public static HistoryLine of(String from, String to, String action, String trigger, Instant changedAt) {
        return new HistoryLine(from, to, action, trigger, changedAt);
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public String action() {
        return action;
    }

    public String trigger() {
        return trigger;
    }

    public Instant changedAt() {
        return changedAt;
    }

    // 이전 상태 → 다음 상태(행위)
    public String transition() {
        return from + " -> " + to + " (" + action + ")";
    }

    @Override
    public String toString() {
        return transition() + " by " + trigger + " at " + changedAt;
    }
}
