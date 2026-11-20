package com.example.inventory.domain.stock;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 10장: 상품 하나와 그 수량. 결제 하나가 잡는 재고는 이런 줄 여러 개다 */
@Getter
public class StockLine {

    private final Long productId;
    private final int quantity;

    @Builder(access = AccessLevel.PRIVATE)
    private StockLine(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public static StockLine of(Long productId, int quantity) {
        return StockLine.builder()
                .productId(productId)
                .quantity(quantity)
                .build();
    }
}
