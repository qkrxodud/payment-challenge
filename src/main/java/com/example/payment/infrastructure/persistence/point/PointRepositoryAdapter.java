package com.example.payment.infrastructure.persistence.point;

import com.example.payment.domain.point.PointRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 8장 결정 3: 포인트도 재고와 같은 이유로 조건부 UPDATE 한 문장으로 쓴다 — 잔액이 쓸 포인트 이상인 때만 바뀐다.
 * 8장 결정 4: 쓰고 되돌릴 때마다 증감 이력(point_history)을 남긴다. 결제 하나에 종류마다 한 줄만 올라간다(uk_point_history_payment_type).
 * 되돌리기는 이력을 먼저 남기고(insert ignore — 이미 있으면 들어가지 않음), 들어간 때만 잔액을 늘린다. 결제 상태 전이로 한 번만 되돌리지만(PaymentHoldings),
 * 그 판단이 어긋나도 잔액이 두 번 늘지 않게 막는다.
 */
@Repository
@RequiredArgsConstructor
public class PointRepositoryAdapter implements PointRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean use(String memberId, Long paymentId, BigDecimal amount, Instant now) {
        int changed = jdbcTemplate.update(
                "update point_wallet set balance = balance - ? where member_id = ? and balance >= ?", amount, memberId, amount);
        if (changed == 0) {
            return false;
        }
        jdbcTemplate.update("insert into point_history (member_id, payment_id, type, amount, created_at) values (?, ?, 'USE', ?, ?)",
                memberId, paymentId, amount, Timestamp.from(now));
        return true;
    }

    @Override
    public void restore(String memberId, Long paymentId, BigDecimal amount, Instant now) {
        int recorded = jdbcTemplate.update(
                "insert ignore into point_history (member_id, payment_id, type, amount, created_at) values (?, ?, 'RESTORE', ?, ?)",
                memberId, paymentId, amount, Timestamp.from(now));
        if (recorded == 0) {
            return;
        }
        jdbcTemplate.update("update point_wallet set balance = balance + ? where member_id = ?", amount, memberId);
    }
}
