package com.example.payment.infrastructure.persistence.coupon.comparison;

import com.example.payment.domain.coupon.Coupon;
import com.example.payment.domain.coupon.CouponRepository;
import com.example.payment.infrastructure.persistence.coupon.CouponRepositoryAdapter;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 비교 구현(8장 결정 5): 사용 기록에 두 번 올라갈 수 없게. 쿠폰을 쓸 때 쿠폰 줄은 바꾸지 않고 사용 기록(coupon_usage, 쿠폰마다 한 줄)을 남긴다.
 * 같은 쿠폰의 두 번째 기록은 coupon_id 가 겹쳐 들어가지 않는다(DuplicateKeyException → 이미 쓰임). 돌려주면 그 결제의 기록을 지운다.
 * coupon_usage 는 이 비교의 시험 DB 에만 둔다(coupon-usage.sql). 쿠폰 줄의 상태는 ISSUED 그대로라 준비 때 확인은 이 기록을 보지 않는다.
 */
@Primary
public class UsageRecordCouponRepository implements CouponRepository {

    private final CouponRepositoryAdapter delegate;
    private final JdbcTemplate jdbcTemplate;

    public UsageRecordCouponRepository(CouponRepositoryAdapter delegate, JdbcTemplate jdbcTemplate) {
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
        try {
            jdbcTemplate.update("insert into coupon_usage (coupon_id, payment_id) values (?, ?)", couponId, paymentId);
            return true;
        } catch (DuplicateKeyException alreadyUsed) {
            return false;
        }
    }

    @Override
    public void restore(Long couponId, Long paymentId) {
        jdbcTemplate.update("delete from coupon_usage where coupon_id = ? and payment_id = ?", couponId, paymentId);
    }
}
