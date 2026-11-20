package com.example.payment.application.payment;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "test.inventory.components="
        + "com.example.payment.infrastructure.persistence.stock.comparison.NoLockStockRepository")
class NoLockStockMeasurement extends StockMeasurement {

    @Override
    String name() {
        return "잠그지 않음";
    }
}
