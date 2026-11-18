package com.example.payment.infrastructure.persistence.coupon;

import com.example.payment.domain.coupon.Coupon;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.coupon.CouponStatus;
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

    // 8장 결정 2: 지금 쿠폰을 읽고, 이미 쓰였으면 쓰지 않는다. 쓸 수 있으면 쓴 것으로 바꾼다(잠그지 않음 — 결정 5 에서 바꾼다)
    @Override
    public boolean use(Long couponId, Long paymentId) {
        String status = jdbcTemplate.queryForObject("select status from coupon where id = ?", String.class, couponId);
        if (CouponStatus.valueOf(status).isIssued()) {
            jdbcTemplate.update("update coupon set status = 'USED', payment_id = ? where id = ?", paymentId, couponId);
            return true;
        }
        return false;
    }
}
