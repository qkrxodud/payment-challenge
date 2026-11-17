package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.payment.comparison.NoLockPaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.annotation.Import;

@Import(NoLockPaymentRepository.class)
@DisplayName("잠금 방식 측정 — 잠그지 않음")
class NoLockLockMeasurement extends LockMeasurement {

    @Override
    String name() {
        return "잠그지 않음";
    }
}
