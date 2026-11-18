package com.example.payment.infrastructure.persistence.coupon;

import com.example.payment.domain.coupon.Coupon;
import com.example.payment.domain.coupon.CouponRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CouponRepositoryAdapter implements CouponRepository {

    private final SpringDataCouponRepository springDataCouponRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Coupon save(Coupon coupon) {
        return springDataCouponRepository.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return springDataCouponRepository.findById(id);
    }

    // 출발 구조: 준비할 때 쓸 수 있는 쿠폰인지 이미 봤으니 쓴 것으로 바꾸기만 한다
    @Override
    public void use(Long couponId, Long paymentId) {
        jdbcTemplate.update("update coupon set status = 'USED', payment_id = ? where id = ?", paymentId, couponId);
    }
}
