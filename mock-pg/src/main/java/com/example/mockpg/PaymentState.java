package com.example.mockpg;

/** 가짜 PG가 주문별로 남기는 결제 상태. 이름은 토스페이먼츠 Payment 객체의 status 값을 따른다 */
public enum PaymentState {
    // 승인된 결제
    DONE,
    // 결제 승인이 실패한 결제
    ABORTED,
    // 승인된 결제가 취소된 결제
    CANCELED;

    public boolean isDone() {
        return this == DONE;
    }

    public boolean isCanceled() {
        return this == CANCELED;
    }
}
