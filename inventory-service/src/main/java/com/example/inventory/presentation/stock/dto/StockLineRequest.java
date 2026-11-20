package com.example.inventory.presentation.stock.dto;

/** 10장: 상품 하나와 그 수량 */
public record StockLineRequest(Long productId, int quantity) {
}
