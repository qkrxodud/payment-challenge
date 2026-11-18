package com.example.payment.infrastructure.persistence.point;

import com.example.payment.domain.point.PointRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointRepositoryAdapter implements PointRepository {

    private final JdbcTemplate jdbcTemplate;

    // 8장 결정 2: 지금 잔액을 읽고, 모자라면 쓰지 않는다. 있으면 거기서 쓴 포인트를 뺀 값을 쓴다(잠그지 않음 — 결정 3 에서 바꾼다)
    @Override
    public boolean use(String memberId, BigDecimal amount) {
        List<BigDecimal> balances = jdbcTemplate.queryForList("select balance from point_wallet where member_id = ?",
                BigDecimal.class, memberId);
        if (balances.isEmpty() || balances.get(0).compareTo(amount) < 0) {
            return false;
        }
        jdbcTemplate.update("update point_wallet set balance = ? where member_id = ?", balances.get(0).subtract(amount),
                memberId);
        return true;
    }
}
