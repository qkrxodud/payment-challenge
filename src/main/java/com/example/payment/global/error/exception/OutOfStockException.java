package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class OutOfStockException extends PaymentChallengeException {

    public OutOfStockException(String orderId) {
        super(ErrorCode.OUT_OF_STOCK, "A product in order " + orderId + " is out of stock.");
    }
}
