package com.example.payment.domain.payment;

public enum PaymentAction {
    AUTHENTICATE,
    FAIL_AUTHENTICATION,
    APPROVE,
    FAIL_APPROVAL,
    // 4장부터: 승인을 요청했지만 카드사 답을 받지 못함
    MARK_APPROVAL_UNKNOWN,
    EXPIRE,
    CAPTURE,
    CANCEL
}
