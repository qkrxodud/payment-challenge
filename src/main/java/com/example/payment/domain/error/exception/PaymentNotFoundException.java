package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class PaymentNotFoundException extends PaymentChallengeException {

    public PaymentNotFoundException(String orderId) {
        super(ErrorCode.PAYMENT_NOT_FOUND, "No payment for order " + orderId + ".");
    }
}
