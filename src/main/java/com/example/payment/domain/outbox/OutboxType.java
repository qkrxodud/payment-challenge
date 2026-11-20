package com.example.payment.domain.outbox;

/**
 * 10장 결정 5: payment_outbox 에 남긴 기록의 종류. 결제 결과(9장, 주문 서비스에 알림)와 재고 명령(재고 서비스에 보내는 예약 확정·해제).
 * 같은 결제의 기록은 종류와 상관없이 번호 순으로 나간다.
 */
public enum OutboxType {
    PAYMENT_RESULT,
    STOCK_CONFIRM,
    STOCK_RELEASE;

    public boolean isStockCommand() {
        return this == STOCK_CONFIRM || this == STOCK_RELEASE;
    }
}
