package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class InsufficientPointException extends PaymentChallengeException {

    public InsufficientPointException(String orderId) {
        super(ErrorCode.INSUFFICIENT_POINT, "Not enough points are left for order " + orderId + ".");
    }
}
