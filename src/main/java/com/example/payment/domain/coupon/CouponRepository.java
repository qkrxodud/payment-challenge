package com.example.payment.domain.coupon;

import java.util.Optional;

public interface CouponRepository {

    Coupon save(Coupon coupon);

    Optional<Coupon> findById(Long id);

    // 아직 쓰지 않은 쿠폰이면 이 결제에 쓴 것으로 남기고 맞다고 답한다. 이미 쓰였으면 아니라고 답한다
    boolean use(Long couponId, Long paymentId);
}
