package com.example.payment.application.payment.comparison.dto;

import java.util.List;

/** 비교 구현(10장 결정 3, 수량만): 재고 명령에 담긴 상품을 함께 싣는다 — 예약 기록이 없어 재고 서비스는 늘릴 수량을 모른다 */
public record QuantityCommandMessage(String command, List<QuantityStockRequest.Line> items) {

    public boolean isRelease() {
        return "RELEASE".equals(command);
    }
}
