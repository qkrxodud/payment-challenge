package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class CouponNotAvailableException extends PaymentChallengeException {

    public CouponNotAvailableException(String orderId) {
        super(ErrorCode.COUPON_NOT_AVAILABLE, "The coupon for order " + orderId + " cannot be used.");
    }
}
