package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.stock.comparison.NoLockStockRepository;
import org.springframework.context.annotation.Import;

@Import(NoLockStockRepository.class)
class NoLockStockMeasurement extends StockMeasurement {

    @Override
    String name() {
        return "잠그지 않음";
    }
}
