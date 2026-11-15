package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class InvalidAmountException extends PaymentChallengeException {

    public InvalidAmountException(String orderId) {
        super(ErrorCode.INVALID_AMOUNT, "Amount of order " + orderId + " is missing or not positive.");
    }
}
