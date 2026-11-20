package com.example.payment.infrastructure.inventory.dto;

import com.example.payment.domain.payment.PaymentItem;
import java.util.List;

/** 10장: 재고 서비스에 보내는 결제 하나에 담긴 상품과 수량 */
public record StockChangeRequest(List<StockLine> items) {

    public static StockChangeRequest of(List<PaymentItem> items) {
        return new StockChangeRequest(items.stream()
                .map(item -> new StockLine(item.getProductId(), item.getQuantity()))
                .toList());
    }

    /** 상품 하나와 그 수량 */
    public record StockLine(Long productId, int quantity) {
    }
}
