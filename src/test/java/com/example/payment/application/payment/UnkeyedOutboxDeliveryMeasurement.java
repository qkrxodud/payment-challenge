package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.UnkeyedEventSender;
import java.util.Objects;
import org.springframework.context.annotation.Import;

@Import(UnkeyedEventSender.class)
class UnkeyedOutboxDeliveryMeasurement extends EventDeliveryMeasurement {

    @Override
    String name() {
        return "남겨 두고 릴레이, 키 없이(기본 파티셔너)";
    }

    @Override
    int pendingEvents() {
        Integer pending = jdbcTemplate.queryForObject("select count(*) from payment_outbox where sent_at is null", Integer.class);
        return Objects.requireNonNullElse(pending, 0);
    }
}
