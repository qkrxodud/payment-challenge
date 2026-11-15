package com.example.payment.domain.payment;

/** 취소 요청 한 건의 상태(5장) */
public enum CancelStatus {
    REQUESTED,  // 취소를 요청하려고 기록함, 카드사 답 전
    DONE,       // 카드사가 취소함
    FAILED,     // 카드사가 취소를 거절함
    UNKNOWN     // 카드사에 취소를 요청했지만 답을 받지 못해 결과를 모름
}
