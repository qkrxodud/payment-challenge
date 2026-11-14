package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class PaymentGatewayFailedException extends PaymentChallengeException {

    public PaymentGatewayFailedException(String orderId, int status) {
        super(ErrorCode.PAYMENT_GATEWAY_FAILED, "The gateway answered " + status + " for order " + orderId + ".");
    }
}
