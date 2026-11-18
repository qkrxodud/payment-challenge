package com.example.payment.infrastructure.persistence.coupon;

import com.example.payment.domain.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCouponRepository extends JpaRepository<Coupon, Long> {
}
