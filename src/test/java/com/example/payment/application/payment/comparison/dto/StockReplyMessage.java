package com.example.payment.application.payment.comparison.dto;

/** 비교 구현(10장 결정 1, 승인 먼저): 재고 서비스가 차감 요청에 돌려주는 답. 줄였으면 decreased 가 맞다 */
public record StockReplyMessage(String orderId, boolean decreased) {
}
