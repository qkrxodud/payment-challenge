package com.example.payment.infrastructure.persistence.point;

import com.example.payment.domain.point.PointRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PointRepositoryAdapter implements PointRepository {

    private final JdbcTemplate jdbcTemplate;

    // 출발 구조: 지금 잔액을 읽고, 거기서 쓴 포인트를 뺀 값을 쓴다
    @Override
    public void use(String memberId, BigDecimal amount) {
        BigDecimal balance = jdbcTemplate.queryForObject("select balance from point_wallet where member_id = ?",
                BigDecimal.class, memberId);
        jdbcTemplate.update("update point_wallet set balance = ? where member_id = ?", balance.subtract(amount), memberId);
    }
}
