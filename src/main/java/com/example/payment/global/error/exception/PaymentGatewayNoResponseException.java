package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class PaymentGatewayNoResponseException extends PaymentChallengeException {

    public PaymentGatewayNoResponseException(String orderId) {
        super(ErrorCode.PAYMENT_GATEWAY_NO_RESPONSE, "No answer from the gateway for order " + orderId + ".");
    }
}
