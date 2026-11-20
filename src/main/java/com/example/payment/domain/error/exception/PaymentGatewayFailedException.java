package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class PaymentGatewayFailedException extends PaymentChallengeException {

    public PaymentGatewayFailedException(String orderId, int status) {
        super(ErrorCode.PAYMENT_GATEWAY_FAILED, "The gateway answered " + status + " for order " + orderId + ".");
    }
}
