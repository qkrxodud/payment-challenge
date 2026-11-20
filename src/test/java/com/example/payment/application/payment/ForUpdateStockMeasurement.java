package com.example.payment.application.payment;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "test.inventory.components="
        + "com.example.payment.infrastructure.persistence.stock.comparison.ForUpdateStockRepository")
class ForUpdateStockMeasurement extends StockMeasurement {

    @Override
    String name() {
        return "select … for update";
    }
}
