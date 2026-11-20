package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

/**
 * 허용 전이 표에 없는 (상태, 행위) 조합을 요청했다.
 * 상태와 행위는 이름으로 받는다(오류 코드 묶음이 결제 애그리거트를 참조하지 않게).
 */
public class InvalidPaymentTransitionException extends PaymentChallengeException {

    public InvalidPaymentTransitionException(String status, String action) {
        super(ErrorCode.INVALID_PAYMENT_TRANSITION, "Cannot " + action + " a payment in " + status + " status.");
    }
}
