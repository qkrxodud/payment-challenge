package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class CancelNotFoundException extends PaymentChallengeException {

    public CancelNotFoundException(String orderId) {
        super(ErrorCode.CANCEL_NOT_FOUND, "No cancel with this idempotency key for order " + orderId + ".");
    }
}
