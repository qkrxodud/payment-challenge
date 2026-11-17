package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;

@DisplayName("잠금 방식 측정 — 운영 구현(결제 줄 잠금)")
class PessimisticLockMeasurement extends LockMeasurement {

    @Override
    String name() {
        return "운영 구현(결제 줄 잠금)";
    }
}
