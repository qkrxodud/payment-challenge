package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.UnkeyedEventSender;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다.
 * 키 없이 보내는 구현은 어느 파티션에 실을지 프로듀서가 정한다. 기본 파티셔너는 한 파티션에 정한 양(batch.size, 16KB)을 채울 때까지 붙어 보내서
 * 이벤트가 몇 건뿐인 시험에서는 모든 이벤트가 한 파티션으로 간다(9장 실행 환경 참고). 키 없는 구현의 시험에서는 이벤트가 파티션을 번갈아 가게
 * 돌아가며 고르는 파티셔너를 쓴다 — 이벤트가 많을 때 파티션이 바뀌는 것을 이벤트 두 건으로 본다. 이 파티셔너는 키도 보지 않으므로 키를 쓰는
 * 운영 구현의 시험에는 쓰지 않는다.
 */
@Tag("known-failing")
@Import(UnkeyedEventSender.class)
@TestPropertySource(properties = "spring.kafka.producer.properties.partitioner.class=org.apache.kafka.clients.producer.RoundRobinPartitioner")
@DisplayName("결제 이벤트 — 비교: 키 없이 보냄(남겨 두고 릴레이로)")
class UnkeyedEventTest extends PaymentEventContract {

    @Override
    int pendingEvents() {
        Integer pending = jdbcTemplate.queryForObject("select count(*) from payment_outbox where sent_at is null", Integer.class);
        return Objects.requireNonNullElse(pending, 0);
    }
}
