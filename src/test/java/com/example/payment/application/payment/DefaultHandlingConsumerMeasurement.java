package com.example.payment.application.payment;

import org.springframework.test.context.TestPropertySource;

/** 11장 측정: 결정 3 비교 — Spring Kafka 기본 처리 */
@TestPropertySource(properties = "test.order.components=com.example.consumercomparison.SpringDefaultErrorHandling")
class DefaultHandlingConsumerMeasurement extends ConsumerMeasurement {

    @Override
    String method() {
        return "결정 3 비교 — Spring Kafka 기본 처리";
    }
}
