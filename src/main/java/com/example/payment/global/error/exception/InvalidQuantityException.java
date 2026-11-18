package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class InvalidQuantityException extends PaymentChallengeException {

    public InvalidQuantityException(String orderId) {
        super(ErrorCode.INVALID_QUANTITY, "A product quantity in order " + orderId + " is not positive.");
    }
}
