package com.example.payment.application.payment;

import java.util.Objects;

/**
 * 12장 시험 도구: 대사 결과 한 줄을 시험이 읽는 꼴. 어긋남 종류(type)와 처리(resolution)는 운영 코드의 이름 그대로 문자열로 둔다 —
 * 출발 구조에는 대사 결과가 없어 운영 코드의 종류를 가리킬 수 없다.
 * 종류: STATUS_BEHIND(우리 기록이 카드사보다 뒤처짐), STATUS_MISMATCH(그 밖에 상태가 다름), AMOUNT_MISMATCH, OURS_ONLY, PG_ONLY.
 * 처리: FOLLOWED(대사가 카드사를 따라 맞춤), NEEDS_REVIEW(운영자가 확인할 것)
 */
public class ReconciliationLine {

    private final String paymentKey;
    private final String type;
    private final String resolution;

    private ReconciliationLine(String paymentKey, String type, String resolution) {
        this.paymentKey = paymentKey;
        this.type = type;
        this.resolution = resolution;
    }

    public static ReconciliationLine of(String paymentKey, String type, String resolution) {
        return new ReconciliationLine(paymentKey, type, resolution);
    }

    public String paymentKey() {
        return paymentKey;
    }

    public String type() {
        return type;
    }

    public String resolution() {
        return resolution;
    }

    public boolean isOf(String key) {
        return Objects.equals(paymentKey, key);
    }

    public boolean needsReview() {
        return "NEEDS_REVIEW".equals(resolution);
    }

    @Override
    public String toString() {
        return paymentKey + " " + type + " " + resolution;
    }
}
