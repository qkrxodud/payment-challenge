package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class InvalidIdempotencyKeyException extends PaymentChallengeException {

    public InvalidIdempotencyKeyException(String orderId) {
        super(ErrorCode.INVALID_IDEMPOTENCY_KEY, "Idempotency-Key of the cancel request for order " + orderId
                + " is missing or too long.");
    }
}
