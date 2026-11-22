package com.example.payment.application.payment;

import com.example.payment.infrastructure.pg.RetryingPaymentGateway;
import org.springframework.context.annotation.Import;

/** 12장 측정: 결정 4 비교 — 다시 시도(답 없음·오류면 두 번 더) */
@Import(RetryingPaymentGateway.class)
class RetryingPgOutageMeasurement extends PgOutageMeasurement {

    @Override
    String method() {
        return "결정 4 비교 — 다시 시도";
    }
}
