package com.example.inventory.domain.error;

import lombok.Getter;

/** 10장: 재고 서비스의 모든 업무 예외의 공통 조상. 메시지(상세)는 로그용 영어 문장이다 */
@Getter
public abstract class InventoryException extends RuntimeException {

    private final ErrorCode errorCode;

    protected InventoryException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
