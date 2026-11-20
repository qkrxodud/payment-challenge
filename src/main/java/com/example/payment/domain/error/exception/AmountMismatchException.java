package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class AmountMismatchException extends PaymentChallengeException {

    public AmountMismatchException(String orderId) {
        super(ErrorCode.AMOUNT_MISMATCH, "Requested amount does not match the prepared amount of order " + orderId + ".");
    }
}
