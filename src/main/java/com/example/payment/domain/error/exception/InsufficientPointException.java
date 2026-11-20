package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class InsufficientPointException extends PaymentChallengeException {

    public InsufficientPointException(String orderId) {
        super(ErrorCode.INSUFFICIENT_POINT, "Not enough points are left for order " + orderId + ".");
    }
}
