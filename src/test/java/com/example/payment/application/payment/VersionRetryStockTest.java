package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.TestPropertySource;

/** 10장부터 재고 서비스 안의 재고 저장소와 재고 서비스를 바꿔 끼운다. 변경 번호 칸은 재고 서비스의 시험 DB 에만 더한다 */
@TestPropertySource(properties = {
        "test.inventory.components="
                + "com.example.payment.infrastructure.persistence.stock.comparison.VersionCheckStockRepository,"
                + "com.example.payment.infrastructure.persistence.stock.comparison.RetryOnStockChangeStockService",
        "test.inventory.properties=spring.sql.init.schema-locations=classpath:inventory-schema.sql,classpath:stock-version.sql"
})
@DisplayName("같은 상품에 몰린 주문의 재고 — 비교: 변경 번호 + 처음부터 다시 시도")
class VersionRetryStockTest extends StockContract {
}
