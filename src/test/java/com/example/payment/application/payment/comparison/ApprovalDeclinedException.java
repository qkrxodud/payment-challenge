package com.example.payment.application.payment.comparison;

/** 비교 구현에서 거절을 호출부에 예외로 알릴 때 쓴다(출발 구조의 PaymentDeclinedException 자리) */
public class ApprovalDeclinedException extends RuntimeException {

    public ApprovalDeclinedException(String orderId) {
        super("Payment for order " + orderId + " was declined.");
    }
}
