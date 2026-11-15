package com.example.mockpg;

/** 가짜 PG가 승인 요청에 내놓을 결과 */
public enum Outcome {
    APPROVE,
    DECLINE,
    // 승인도 거절도 아닌 서버 오류(500)
    FAIL,
    // 요청이 승인 처리까지 닿지 않음. 기다리게 한 뒤 아무것도 기록하지 않고 오류로 끝낸다(4장부터)
    LOST;

    public boolean isDecline() {
        return this == DECLINE;
    }

    public boolean isFail() {
        return this == FAIL;
    }

    public boolean isLost() {
        return this == LOST;
    }
}
