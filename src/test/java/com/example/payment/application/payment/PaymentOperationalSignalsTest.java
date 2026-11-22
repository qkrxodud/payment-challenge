package com.example.payment.application.payment;

import com.example.inventory.application.cleanup.ProcessedCommandCleanup;
import com.example.order.application.cleanup.ReceivedEventCleanup;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("운영 지표와 정리 — 운영 구현")
class PaymentOperationalSignalsTest extends OperationalSignalsContract {

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    OutboxCleanup outboxCleanup;

    @Autowired
    DailyReconciler dailyReconciler;

    @Override
    Optional<Double> paymentMeter(String name) {
        return gaugeIn(meterRegistry, name);
    }

    @Override
    Optional<Double> orderMeter(String name) {
        return gaugeIn(orderServer.bean(MeterRegistry.class), name);
    }

    @Override
    Optional<Double> inventoryMeter(String name) {
        return gaugeIn(inventory.bean(MeterRegistry.class), name);
    }

    @Override
    void cleanUpPayments(Instant now) {
        outboxCleanup.cleanUp(now);
    }

    @Override
    void cleanUpOrders(Instant now) {
        orderServer.bean(ReceivedEventCleanup.class).cleanUp(now);
    }

    @Override
    void cleanUpInventory(Instant now) {
        inventory.bean(ProcessedCommandCleanup.class).cleanUp(now);
    }

    @Override
    void reconcile(LocalDate day) {
        dailyReconciler.reconcile(day);
    }

    // 그 서비스의 지표 보관함에서 이 이름의 값을 읽는다(읽을 때마다 새로 센다). 없으면 비어 있다
    static Optional<Double> gaugeIn(MeterRegistry registry, String name) {
        return Optional.ofNullable(registry.find(name).gauge()).map(Gauge::value);
    }
}
