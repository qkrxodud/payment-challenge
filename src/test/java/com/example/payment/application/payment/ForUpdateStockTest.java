package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.stock.comparison.ForUpdateStockRepository;
import org.junit.jupiter.api.DisplayName;
import org.springframework.context.annotation.Import;

@Import(ForUpdateStockRepository.class)
@DisplayName("같은 상품에 몰린 주문의 재고 — 비교: select … for update")
class ForUpdateStockTest extends StockContract {
}
