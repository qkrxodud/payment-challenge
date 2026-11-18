package com.example.payment.infrastructure.persistence.coupon.comparison;

import com.example.payment.domain.coupon.Coupon;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.domain.coupon.CouponStatus;
import com.example.payment.infrastructure.persistence.coupon.CouponRepositoryAdapter;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** 비교 구현(8장 결정 5): 읽고 확인한 뒤 저장(결정 2~4). 지금 쿠폰을 읽고, 쓸 수 있으면 쓴 것으로 바꾼다. 돌려주기·찾기는 운영과 같다 */
@Primary
public class ReadThenSaveCouponRepository implements CouponRepository {

    private final CouponRepositoryAdapter delegate;
    private final JdbcTemplate jdbcTemplate;

    public ReadThenSaveCouponRepository(CouponRepositoryAdapter delegate, JdbcTemplate jdbcTemplate) {
        this.delegate = delegate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Coupon save(Coupon coupon) {
        return delegate.save(coupon);
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public boolean use(Long couponId, Long paymentId) {
        String status = jdbcTemplate.queryForObject("select status from coupon where id = ?", String.class, couponId);
        if (CouponStatus.valueOf(status).isIssued()) {
            jdbcTemplate.update("update coupon set status = 'USED', payment_id = ? where id = ?", paymentId, couponId);
            return true;
        }
        return false;
    }

    @Override
    public void restore(Long couponId, Long paymentId) {
        delegate.restore(couponId, paymentId);
    }
}
