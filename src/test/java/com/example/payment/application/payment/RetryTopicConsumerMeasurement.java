package com.example.payment.application.payment;

import org.springframework.test.context.TestPropertySource;

/** 11장 측정: 결정 3 비교 — 다시 시도 토픽 */
@TestPropertySource(properties = "test.order.components=com.example.consumercomparison.RetryTopicOrderConsumer")
class RetryTopicConsumerMeasurement extends ConsumerMeasurement {

    @Override
    String method() {
        return "결정 3 비교 — 다시 시도 토픽";
    }
}
