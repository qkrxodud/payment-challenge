package com.example.order.domain.error;

import lombok.Getter;

/** 11장: 주문 서비스의 업무 예외의 공통 조상. 메시지(상세)는 로그용 영어 문장이다 */
@Getter
public abstract class OrderServiceException extends RuntimeException {

    private final ErrorCode errorCode;

    protected OrderServiceException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
