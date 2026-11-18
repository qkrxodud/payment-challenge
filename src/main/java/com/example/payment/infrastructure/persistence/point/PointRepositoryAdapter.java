package com.example.payment.infrastructure.persistence.point;

import com.example.payment.domain.point.PointRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 8장 결정 3: 포인트도 재고와 같은 이유로 조건부 UPDATE 한 문장으로 쓴다 — 잔액이 쓸 포인트 이상인 때만 바뀐다 */
@Repository
@RequiredArgsConstructor
public class PointRepositoryAdapter implements PointRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean use(String memberId, BigDecimal amount) {
        return jdbcTemplate.update("update point_wallet set balance = balance - ? where member_id = ? and balance >= ?",
                amount, memberId, amount) == 1;
    }
}
