package com.example.payment.global.error.exception;

import com.example.payment.global.error.ErrorCode;
import com.example.payment.global.error.PaymentChallengeException;

public class CancelAmountExceedsBalanceException extends PaymentChallengeException {

    public CancelAmountExceedsBalanceException(String orderId) {
        super(ErrorCode.CANCEL_AMOUNT_EXCEEDS_BALANCE, "Cancel amount is larger than the balance of order " + orderId + ".");
    }
}
