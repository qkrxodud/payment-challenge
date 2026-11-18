package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class ProductNotFoundException extends PaymentChallengeException {

    public ProductNotFoundException(String orderId) {
        super(ErrorCode.PRODUCT_NOT_FOUND, "A product in order " + orderId + " was not found.");
    }
}
