package com.example.payment.application.payment;

import java.util.Objects;
import org.junit.jupiter.api.DisplayName;

/** 9장 결정 4부터 결제 번호를 키로 보낸다. 출발 구조부터 결정 3까지는 키 없이 보내 이 시험에 돌아가며 고르는 파티셔너를 썼다(UnkeyedEventTest 참고) */
@DisplayName("결제 이벤트 — 운영 구현")
class PaymentEventTest extends PaymentEventContract {

    // 9장 결정 2부터: 릴레이가 아직 보내지 않은 결과
    @Override
    int pendingEvents() {
        Integer pending = jdbcTemplate.queryForObject("select count(*) from payment_outbox where sent_at is null", Integer.class);
        return Objects.requireNonNullElse(pending, 0);
    }
}
