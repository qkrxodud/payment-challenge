package com.example.payment.domain.coupon;

import java.util.Optional;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findById(Long id);

    // 쿠폰을 이 결제에 쓴 것으로 남긴다
    void use(Long couponId, Long paymentId);
}
