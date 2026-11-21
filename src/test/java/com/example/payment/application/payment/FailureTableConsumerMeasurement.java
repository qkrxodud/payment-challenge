package com.example.payment.application.payment;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.test.context.TestPropertySource;

/** 11장 측정: 결정 4 비교 — DB 실패 표 */
@TestPropertySource(properties = "test.order.components=com.example.consumercomparison.FailureTableErrorHandling")
class FailureTableConsumerMeasurement extends ConsumerMeasurement {

    @Override
    String method() {
        return "결정 4 비교 — DB 실패 표";
    }

    @Override
    Set<String> deadLetterBodies() {
        return orderServer.rows("select body from failed_message").stream()
                .map(row -> (String) row.get("body"))
                .collect(Collectors.toSet());
    }
}
