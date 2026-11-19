package com.example.payment.application.payment;

import java.util.Objects;
import java.util.Objects;

class OutboxDeliveryMeasurement extends EventDeliveryMeasurement {

    @Override
    String name() {
        return "남겨 두고 릴레이(운영, 결제 번호 키)";
    }

    @Override
    int pendingEvents() {
        Integer pending = jdbcTemplate.queryForObject("select count(*) from payment_outbox where sent_at is null", Integer.class);
        return Objects.requireNonNullElse(pending, 0);
    }
}
