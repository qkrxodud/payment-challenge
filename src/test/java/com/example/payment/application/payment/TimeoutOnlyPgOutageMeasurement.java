package com.example.payment.application.payment;

import com.example.payment.infrastructure.pg.TimeoutOnlyPaymentGateway;
import org.springframework.context.annotation.Import;

/** 12장 측정: 결정 4 비교 — 시간 제한만(11장까지) */
@Import(TimeoutOnlyPaymentGateway.class)
class TimeoutOnlyPgOutageMeasurement extends PgOutageMeasurement {

    @Override
    String method() {
        return "결정 4 비교 — 시간 제한만";
    }
}
