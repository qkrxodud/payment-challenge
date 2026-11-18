package com.example.payment.infrastructure.persistence.point.comparison;

import com.example.payment.domain.point.PointRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** 비교 구현(8장 결정 4): 이력 없이 잔액만 고치기. 쓸 때는 운영과 같은 조건부 UPDATE, 돌려줄 때는 잔액만 늘린다 */
@Primary
public class BalanceOnlyPointRepository implements PointRepository {

    private final JdbcTemplate jdbcTemplate;

    public BalanceOnlyPointRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean use(String memberId, Long paymentId, BigDecimal amount, Instant now) {
        return jdbcTemplate.update("update point_wallet set balance = balance - ? where member_id = ? and balance >= ?",
                amount, memberId, amount) == 1;
    }

    @Override
    public void restore(String memberId, Long paymentId, BigDecimal amount, Instant now) {
        jdbcTemplate.update("update point_wallet set balance = balance + ? where member_id = ?", amount, memberId);
    }
}
