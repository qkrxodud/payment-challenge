package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class InvalidCancelReasonException extends PaymentChallengeException {

    public InvalidCancelReasonException(String orderId) {
        super(ErrorCode.INVALID_CANCEL_REASON, "Cancel reason of order " + orderId + " is missing.");
    }
}
