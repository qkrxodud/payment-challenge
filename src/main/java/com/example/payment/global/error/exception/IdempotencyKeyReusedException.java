package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class IdempotencyKeyReusedException extends PaymentChallengeException {

    public IdempotencyKeyReusedException(String orderId) {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key was already used for a different cancel of order "
                + orderId + ".");
    }
}
