package com.example.payment.infrastructure.persistence.stock.comparison;

/** 비교 구현(8장 결정 3, 변경 번호): 읽은 뒤 다른 주문이 재고를 바꿨다. 인증 트랜잭션을 처음부터 다시 해야 한다 */
public class StockChangedException extends RuntimeException {

    public StockChangedException(Long productId) {
        super("The stock of product " + productId + " was changed by another order.");
    }
}
