package com.example.inventory.global.error.dto;

import com.example.inventory.domain.error.ErrorCode;

/** 10장: 재고 서비스의 오류 응답 {code, message} */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getClientMessage());
    }
}
