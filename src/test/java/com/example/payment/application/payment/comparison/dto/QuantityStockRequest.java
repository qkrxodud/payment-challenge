package com.example.payment.application.payment.comparison.dto;

import java.util.List;

/** 비교 구현(10장 결정 3, 수량만): 결제 하나에 담긴 상품과 수량. 어느 결제의 요청인지는 싣지 않는다 */
public record QuantityStockRequest(List<Line> items) {

    /** 상품 하나와 그 수량 */
    public record Line(Long productId, int quantity) {
    }
}
