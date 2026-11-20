package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class ProductNotFoundException extends PaymentChallengeException {

    public ProductNotFoundException(String orderId) {
        super(ErrorCode.PRODUCT_NOT_FOUND, "A product in order " + orderId + " was not found.");
    }
}
