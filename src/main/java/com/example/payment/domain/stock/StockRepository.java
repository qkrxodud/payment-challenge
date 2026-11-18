package com.example.payment.domain.stock;

/** 8장: 상품마다 한 줄인 재고 */
public interface StockRepository {

    // 이 상품의 재고를 수량만큼 줄인다
    void decrease(Long productId, int quantity);
}
