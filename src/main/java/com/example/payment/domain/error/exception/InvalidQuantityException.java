package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class InvalidQuantityException extends PaymentChallengeException {

    public InvalidQuantityException(String orderId) {
        super(ErrorCode.INVALID_QUANTITY, "A product quantity in order " + orderId + " is not positive.");
    }
}
