package com.example.mockpg;

/** 가짜 PG가 주문별로 남기는 결제 상태. 이름은 토스페이먼츠 Payment 객체의 status 값을 따른다 */
public enum PaymentState {
    // 승인된 결제
    DONE,
    // 결제 승인이 실패한 결제
    ABORTED,
    // 승인된 결제가 부분 취소된 결제(5장부터)
    PARTIAL_CANCELED,
    // 승인된 결제가 취소된 결제
    CANCELED,
    // 8장: 결제 인증 뒤 승인 요청이 오지 않아 유효 시간이 지나 만료된 결제(토스페이먼츠 문서: 인증이 유효한 10분 안에 승인 API 를
    // 부르지 않으면 만료). 승인이 없으니 취소할 수 없다
    EXPIRED;

    public boolean isCanceled() {
        return this == CANCELED;
    }

    // 승인이 남아 있지 않아 취소할 수 없는 결제(승인 실패, 8장부터 만료)
    public boolean isNotCancelable() {
        return this == ABORTED || this == EXPIRED;
    }
}
