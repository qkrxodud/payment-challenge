package com.example.payment.domain.error;

/**
 * 오류의 성격. HTTP 상태 코드는 웹 계층(GlobalExceptionHandler)이 이 값을 보고 정한다 — 도메인은 HTTP 를 모른다.
 */
public enum ErrorType {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    // 5장: 형식은 맞지만 처리할 수 없는 요청(같은 멱등키에 다른 내용)
    UNPROCESSABLE,
    UPSTREAM_FAILED,
    UPSTREAM_NO_RESPONSE,
    INTERNAL
}
