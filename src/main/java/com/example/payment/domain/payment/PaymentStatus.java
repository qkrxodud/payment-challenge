package com.example.payment.domain.payment;

public enum PaymentStatus {
    READY,              // 결제 생성, 인증 전
    AUTHENTICATED,      // 결제수단 인증 완료, 승인 전
    AUTH_FAILED,        // 인증 실패
    APPROVED,           // 승인 완료, 매입 전
    APPROVAL_FAILED,    // 승인 거절
    EXPIRED,            // 승인 요청 없이 유효 시간이 지나 만료
    CAPTURED,           // 매입 완료(대금 청구 확정)
    APPROVAL_CANCELED,  // 매입 전 취소(승인 취소)
    CAPTURE_CANCELED    // 매입 후 취소(매입 취소)
}
