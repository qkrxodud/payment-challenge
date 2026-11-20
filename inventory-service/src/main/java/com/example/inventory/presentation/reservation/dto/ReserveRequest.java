package com.example.inventory.presentation.reservation.dto;

import com.example.inventory.domain.stock.StockLine;
import java.util.List;

/** 10장 결정 3: 예약 요청. 예약 번호는 결제 번호다 */
public record ReserveRequest(Long reservationId, List<Line> items) {

    public List<StockLine> toLines() {
        return items.stream()
                .map(item -> StockLine.of(item.productId(), item.quantity()))
                .toList();
    }

    /** 상품 하나와 그 수량 */
    public record Line(Long productId, int quantity) {
    }
}
