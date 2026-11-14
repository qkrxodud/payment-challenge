package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

/**
 * 허용 전이 표에 없는 (상태, 행위) 조합을 요청했다.
 * global 은 도메인을 참조하지 않으므로 상태와 행위는 이름으로 받는다.
 */
public class InvalidPaymentTransitionException extends PaymentChallengeException {

    public InvalidPaymentTransitionException(String status, String action) {
        super(ErrorCode.INVALID_PAYMENT_TRANSITION, "Cannot " + action + " a payment in " + status + " status.");
    }
}
