package com.example.payment.application.payment;

class OperationalSagaMeasurement extends SagaMeasurement {

    @Override
    String name() {
        return "운영(예약 먼저 · 답 없으면 닫고 해제 · 확정·해제는 Outbox)";
    }
}
