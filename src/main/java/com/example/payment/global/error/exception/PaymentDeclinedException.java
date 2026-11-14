package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class PaymentDeclinedException extends PaymentChallengeException {

    public PaymentDeclinedException(String orderId, String declineCode) {
        super(ErrorCode.PAYMENT_DECLINED, "Payment for order " + orderId + " was declined with " + declineCode + ".");
    }
}
