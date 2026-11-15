package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class AmountMismatchException extends PaymentChallengeException {

    public AmountMismatchException(String orderId) {
        super(ErrorCode.AMOUNT_MISMATCH, "Requested amount does not match the prepared amount of order " + orderId + ".");
    }
}
