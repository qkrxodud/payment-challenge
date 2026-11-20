package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class CancelAmountExceedsBalanceException extends PaymentChallengeException {

    public CancelAmountExceedsBalanceException(String orderId) {
        super(ErrorCode.CANCEL_AMOUNT_EXCEEDS_BALANCE, "Cancel amount is larger than the balance of order " + orderId + ".");
    }
}
