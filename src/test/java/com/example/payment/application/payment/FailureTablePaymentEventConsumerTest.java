package com.example.payment.application.payment;

import com.example.payment.DeadLetter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.TestPropertySource;

/**
 * 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다.
 * 실패 모음은 받는 쪽 DB 의 실패 표(failed_message)이고, 다시 넣기는 실패 표의 줄을 원래 토픽에 다시 싣고 그 줄을 지운다.
 */
@Tag("known-failing")
@TestPropertySource(properties = "test.order.components=com.example.consumercomparison.FailureTableErrorHandling")
@DisplayName("결제 결과 받기(주문 서비스) — 비교(결정 4): 다시 시도를 다 쓰면 받는 쪽 저장소의 실패 표에 남김")
class FailureTablePaymentEventConsumerTest extends PaymentEventConsumerContract {

    @Override
    List<DeadLetter> deadLetters() {
        return orderServer.rows("select message_key, body, cause from failed_message order by id").stream()
                .map(row -> DeadLetter.of((String) row.get("message_key"), (String) row.get("body"), (String) row.get("cause"), 0, 0))
                .toList();
    }

    @Override
    void replayDeadLetters() {
        for (Map<String, Object> row : orderServer.rows("select id, message_key, body from failed_message order by id")) {
            feed.send(orderServer.topic(), (String) row.get("message_key"), (String) row.get("body"));
            orderServer.execute("delete from failed_message where id = " + row.get("id"));
        }
    }
}
