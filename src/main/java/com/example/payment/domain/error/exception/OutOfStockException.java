package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class OutOfStockException extends PaymentChallengeException {

    public OutOfStockException(String orderId) {
        super(ErrorCode.OUT_OF_STOCK, "A product in order " + orderId + " is out of stock.");
    }
}
