package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.stock.comparison.RetryOnStockChangeStockService;
import org.springframework.test.context.TestPropertySource;

/** 10장부터 재고 서비스 안의 재고 저장소와 재고 서비스를 바꿔 끼운다 */
@TestPropertySource(properties = {
        "test.inventory.components="
                + "com.example.payment.infrastructure.persistence.stock.comparison.VersionCheckStockRepository,"
                + "com.example.payment.infrastructure.persistence.stock.comparison.RetryOnStockChangeStockService",
        "test.inventory.properties=spring.sql.init.schema-locations=classpath:inventory-schema.sql,classpath:stock-version.sql"
})
class VersionRetryStockMeasurement extends StockMeasurement {

    @Override
    String name() {
        return "변경 번호 + 다시 시도(최대 " + RetryOnStockChangeStockService.MAX_ATTEMPTS + "번)";
    }

    @Override
    String retries() {
        return " | 다시 시도 " + RetryOnStockChangeStockService.takeRetries() + "번";
    }
}
