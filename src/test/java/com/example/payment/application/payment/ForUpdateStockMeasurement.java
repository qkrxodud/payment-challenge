package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.stock.comparison.ForUpdateStockRepository;
import org.springframework.context.annotation.Import;

@Import(ForUpdateStockRepository.class)
class ForUpdateStockMeasurement extends StockMeasurement {

    @Override
    String name() {
        return "select … for update";
    }
}
