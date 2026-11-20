package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class IdempotencyKeyReusedException extends PaymentChallengeException {

    public IdempotencyKeyReusedException(String orderId) {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key was already used for a different cancel of order "
                + orderId + ".");
    }
}
