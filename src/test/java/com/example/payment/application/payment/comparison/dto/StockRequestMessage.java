package com.example.payment.application.payment.comparison.dto;

import java.util.List;

/** 비교 구현(10장 결정 1, 승인 먼저): 승인된 결제가 재고 서비스에 Kafka 로 보내는 재고 차감 요청 */
public record StockRequestMessage(String orderId, List<Line> items) {

    /** 상품 하나와 그 수량 */
    public record Line(Long productId, int quantity) {
    }
}
