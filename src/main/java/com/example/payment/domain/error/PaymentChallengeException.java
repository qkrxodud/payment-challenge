package com.example.payment.domain.error;

import lombok.Getter;

/**
 * 서버의 모든 업무 예외의 공통 조상. 오류 코드를 들고 있어서 응답 변환을 한 곳에서 할 수 있다.
 * 메시지(상세)는 로그용 영어 문장이다.
 */
@Getter
public abstract class PaymentChallengeException extends RuntimeException {

    private final ErrorCode errorCode;

    protected PaymentChallengeException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
