package com.example.payment.application.payment;

import com.example.payment.infrastructure.persistence.stock.comparison.NoLockStockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@Import(NoLockStockRepository.class)
@DisplayName("같은 상품에 몰린 주문의 재고 — 비교: 잠그지 않고 읽고 확인한 뒤 씀(결정 2까지)")
class NoLockStockTest extends StockContract {
}
