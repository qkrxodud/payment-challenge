package com.example.payment.infrastructure.pg.dto;

import java.util.List;
import java.util.Objects;

// 카드사 결제 조회·취소 응답에서 읽는 필드만 둔다. 취소 이력(cancels)은 5장부터 읽는다
public record PgPaymentResponse(String orderId, String status, List<PgCancel> cancels) {

    // 취소 응답의 마지막 취소가 방금 요청한 취소다
    public PgCancel lastCancel() {
        List<PgCancel> known = knownCancels();
        return known.get(known.size() - 1);
    }

    public List<PgCancel> doneCancels() {
        return knownCancels().stream()
                .filter(PgCancel::isDone)
                .toList();
    }

    private List<PgCancel> knownCancels() {
        return Objects.requireNonNullElse(cancels, List.of());
    }
}
