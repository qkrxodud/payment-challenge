package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class DuplicateOrderException extends PaymentChallengeException {

    public DuplicateOrderException(String orderId) {
        super(ErrorCode.DUPLICATE_ORDER, "Order " + orderId + " was already prepared with a different amount.");
    }
}
