package com.example.payment.application.payment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;

@DisplayName("운영 지표와 정리 — 운영 구현")
class PaymentOperationalSignalsTest extends OperationalSignalsContract {

    // 12장 출발 구조: 결제 서버·주문 서비스·재고 서비스 모두 운영 지표가 없다
    @Override
    Optional<Double> paymentMeter(String name) {
        return Optional.empty();
    }

    @Override
    Optional<Double> orderMeter(String name) {
        return Optional.empty();
    }

    @Override
    Optional<Double> inventoryMeter(String name) {
        return Optional.empty();
    }

    // 12장 출발 구조: 보낸 결제 결과와 받은 기록을 지우는 정리가 없다
    @Override
    void cleanUpPayments(Instant now) {
    }

    @Override
    void cleanUpOrders(Instant now) {
    }

    @Override
    void cleanUpInventory(Instant now) {
    }

    // 12장 출발 구조: 대사가 없다
    @Override
    void reconcile(LocalDate day) {
    }
}
