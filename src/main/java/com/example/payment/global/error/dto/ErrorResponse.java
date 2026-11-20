package com.example.payment.global.error.dto;

import com.example.payment.domain.error.ErrorCode;

/**
 * 모든 오류 응답의 모양: {"code": "...", "message": "..."}
 */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getClientMessage());
    }
}
