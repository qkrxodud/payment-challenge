package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class InvalidAmountException extends PaymentChallengeException {

    public InvalidAmountException(String orderId) {
        super(ErrorCode.INVALID_AMOUNT, "Amount of order " + orderId + " is missing or not positive.");
    }
}
