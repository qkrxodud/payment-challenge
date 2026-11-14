package com.example.payment.domain.payment;

public enum PaymentAction {
    AUTHENTICATE,
    FAIL_AUTHENTICATION,
    APPROVE,
    FAIL_APPROVAL,
    EXPIRE,
    CAPTURE,
    CANCEL
}
