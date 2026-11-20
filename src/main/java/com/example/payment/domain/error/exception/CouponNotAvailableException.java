package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class CouponNotAvailableException extends PaymentChallengeException {

    public CouponNotAvailableException(String orderId) {
        super(ErrorCode.COUPON_NOT_AVAILABLE, "The coupon for order " + orderId + " cannot be used.");
    }
}
