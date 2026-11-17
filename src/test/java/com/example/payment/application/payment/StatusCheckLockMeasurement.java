package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.payment.comparison.StatusCheckPaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.annotation.Import;

@Import(StatusCheckPaymentRepository.class)
@DisplayName("잠금 방식 측정 — 상태 조건 UPDATE, 어긋나면 409")
class StatusCheckLockMeasurement extends LockMeasurement {

    @Override
    String name() {
        return "상태 조건 UPDATE, 어긋나면 409";
    }
}
