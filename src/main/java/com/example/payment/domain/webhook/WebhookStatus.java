package com.example.payment.domain.webhook;

/** 받은 웹훅 한 건의 처리 상태(6장) */
public enum WebhookStatus {
    RECEIVED,   // 받아서 남김, 처리 전(카드사 조회에 실패했으면 다음 처리 때 다시 본다)
    PROCESSED,  // 카드사 조회로 결제를 맞춤
    IGNORED;    // 우리가 모르는 주문이라 볼 결제가 없음

    public boolean isReceived() {
        return this == RECEIVED;
    }
}
