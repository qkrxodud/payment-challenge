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

    // 8장 결정 5: 쿠폰은 조건부 UPDATE 한 문장으로 쓴다 — 아직 쓰지 않은(ISSUED) 때만 바뀐다. 바뀐 줄이 없으면 이미 다른 결제에 쓰였다(409).
    // 읽고 확인한 뒤 쓰면(결정 2) 겹친 두 주문이 둘 다 쓸 수 있는 쿠폰으로 읽어 둘 다 썼다. 이 문장은 쿠폰 줄을 잠그고 최신 상태로 조건을 본다
    @Override
    public boolean use(Long couponId, Long paymentId) {
        return jdbcTemplate.update("update coupon set status = 'USED', payment_id = ? where id = ? and status = 'ISSUED'",
                paymentId, couponId) == 1;
    }

    @Override
    public void restore(Long couponId, Long paymentId) {
        jdbcTemplate.update("update coupon set status = 'ISSUED', payment_id = null where id = ? and payment_id = ? and status = 'USED'",
                couponId, paymentId);
    }
}
