package com.example.mockpg;

/** 가짜 PG가 승인 요청에 내놓을 결과 */
public enum Outcome {
    APPROVE,
    DECLINE,
    // 승인도 거절도 아닌 서버 오류(500)
    FAIL;

    public boolean isDecline() {
        return this == DECLINE;
    }

    public boolean isFail() {
        return this == FAIL;
    }
}
