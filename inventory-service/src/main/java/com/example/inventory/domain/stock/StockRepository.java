package com.example.inventory.domain.stock;

/** 10장: 상품마다 한 줄인 재고(8장에 결제 서버에 있던 것) */
public interface StockRepository {

    // 재고가 수량만큼 있으면 줄이고 맞다고 답한다. 모자라면 줄이지 않고 아니라고 답한다
    boolean decrease(Long productId, int quantity);

    // 줄였던 재고를 돌려준다
    void increase(Long productId, int quantity);
}
