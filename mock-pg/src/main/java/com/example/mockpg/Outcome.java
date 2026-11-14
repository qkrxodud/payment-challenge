package com.example.mockpg;

/** 가짜 PG가 승인 요청에 내놓을 결과 */
public enum Outcome {
    APPROVE,
    DECLINE;

    public boolean isDecline() {
        return this == DECLINE;
    }
}
