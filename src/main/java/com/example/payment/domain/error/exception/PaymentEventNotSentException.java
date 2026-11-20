package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

public class PaymentEventNotSentException extends PaymentChallengeException {

    public PaymentEventNotSentException(String eventId, String reason) {
        super(ErrorCode.PAYMENT_EVENT_NOT_SENT, "The broker did not confirm payment event " + eventId + " (" + reason + ").");
    }
}
