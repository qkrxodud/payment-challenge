package com.example.payment.infrastructure.inventory.dto;

/** 10장: 재고 서비스에 보내는 줄이거나 늘릴 수량 */
public record StockChangeRequest(int quantity) {
}
