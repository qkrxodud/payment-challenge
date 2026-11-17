package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.payment.comparison.VersionCheckPaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.annotation.Import;

@Import(VersionCheckPaymentRepository.class)
@DisplayName("잠금 방식 측정 — 변경 번호 비교, 어긋나면 409")
class VersionCheckLockMeasurement extends LockMeasurement {

    @Override
    String name() {
        return "변경 번호 비교, 어긋나면 409";
    }
}
