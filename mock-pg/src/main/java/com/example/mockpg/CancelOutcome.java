package com.example.mockpg;

/** 가짜 PG가 취소 요청 하나를 처리한 결과(5장) */
public enum CancelOutcome {
    CANCELED,
    // 이미 전액 취소된 결제
    ALREADY_CANCELED,
    // 승인되지 않아 취소할 것이 없는 결제
    NOT_CANCELABLE_PAYMENT,
    // 잔액을 넘거나 0원 이하인 취소 금액
    NOT_CANCELABLE_AMOUNT;

    public boolean isCanceled() {
        return this == CANCELED;
    }
}
