package com.example.inventory.presentation.stock.dto;

import com.example.inventory.domain.stock.StockLine;
import java.util.List;

/** 10장: 결제 하나에 담긴 상품과 수량 */
public record StockChangeRequest(List<StockLineRequest> items) {

    public List<StockLine> toLines() {
        return items.stream()
                .map(item -> StockLine.of(item.productId(), item.quantity()))
                .toList();
    }
}
