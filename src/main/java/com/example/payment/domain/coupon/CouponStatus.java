package com.example.payment.domain.coupon;

public enum CouponStatus {
    ISSUED,     // 발급됨, 쓸 수 있음
    USED;       // 결제에 쓰임

    public boolean isIssued() {
        return this == ISSUED;
    }
}
