package com.example.inventory.domain.error;

/** 오류의 성격. HTTP 상태 코드는 웹 계층(GlobalExceptionHandler)이 이 값을 보고 정한다 */
public enum ErrorType {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    INTERNAL
}
