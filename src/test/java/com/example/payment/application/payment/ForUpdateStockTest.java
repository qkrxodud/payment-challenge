package com.example.payment.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.TestPropertySource;

/** 10장부터 재고 서비스 안의 재고 저장소를 바꿔 끼운다 */
@TestPropertySource(properties = "test.inventory.components="
        + "com.example.payment.infrastructure.persistence.stock.comparison.ForUpdateStockRepository")
@DisplayName("같은 상품에 몰린 주문의 재고 — 비교: select … for update")
class ForUpdateStockTest extends StockContract {
}
