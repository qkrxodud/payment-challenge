package com.example.payment.domain.stock;

/** 8장: 상품마다 한 줄인 재고 */
public interface StockRepository {

    // 재고가 수량만큼 있으면 줄이고 맞다고 답한다. 모자라면 줄이지 않고 아니라고 답한다
    boolean decrease(Long productId, int quantity);

    // 8장 결정 4: 잡아 둔 재고를 돌려준다
    void increase(Long productId, int quantity);
}
