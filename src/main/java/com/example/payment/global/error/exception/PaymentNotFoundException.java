package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class PaymentNotFoundException extends PaymentChallengeException {

    public PaymentNotFoundException(String orderId) {
        super(ErrorCode.PAYMENT_NOT_FOUND, "No payment for order " + orderId + ".");
    }
}
