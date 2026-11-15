package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class InvalidIdempotencyKeyException extends PaymentChallengeException {

    public InvalidIdempotencyKeyException(String orderId) {
        super(ErrorCode.INVALID_IDEMPOTENCY_KEY, "Idempotency-Key of the cancel request for order " + orderId
                + " is missing or too long.");
    }
}
