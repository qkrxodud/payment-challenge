package com.example.payment.application.payment;

import org.springframework.test.context.TestPropertySource;

/** 11장 측정: 결정 4 비교 — 다 쓰면 로그만 */
@TestPropertySource(properties = "test.order.components=com.example.consumercomparison.LogOnlyErrorHandling")
class LogOnlyConsumerMeasurement extends ConsumerMeasurement {

    @Override
    String method() {
        return "결정 4 비교 — 다 쓰면 로그만";
    }
}
