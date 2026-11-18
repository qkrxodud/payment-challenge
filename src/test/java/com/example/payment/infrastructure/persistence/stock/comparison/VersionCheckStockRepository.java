package com.example.payment.infrastructure.persistence.stock.comparison;

import com.example.payment.domain.stock.StockRepository;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 비교 구현(8장 결정 3): 변경 번호(@Version 과 같은 방법). 재고와 변경 번호를 잠그지 않고 읽고, 확인한 뒤 "읽은 번호 그대로일 때만" 줄이며 번호를 올린다
 * (Hibernate 가 @Version 엔티티를 고칠 때 내는 update … where … and version = ? 와 같은 모양). 번호가 어긋나 바뀐 줄이 없으면
 * StockChangedException 으로 알린다 — 같은 트랜잭션 안에서 다시 읽으면 같은 스냅숏(REPEATABLE READ)을 다시 보므로,
 * 다시 시도는 인증 트랜잭션을 처음부터 다시 하는 승인 서비스(RetryOnStockChangeConfirmService)가 한다.
 * version 칸은 이 비교의 시험 DB 에만 더한다(stock-version.sql).
 */
@Primary
public class VersionCheckStockRepository implements StockRepository {

    private final JdbcTemplate jdbcTemplate;

    public VersionCheckStockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean decrease(Long productId, int quantity) {
        Map<String, Object> read = jdbcTemplate.queryForMap("select quantity, version from stock where product_id = ?",
                productId);
        int current = ((Number) read.get("quantity")).intValue();
        long version = ((Number) read.get("version")).longValue();
        if (current < quantity) {
            return false;
        }
        int changed = jdbcTemplate.update(
                "update stock set quantity = ?, version = version + 1 where product_id = ? and version = ?",
                current - quantity, productId, version);
        if (changed == 0) {
            throw new StockChangedException(productId);
        }
        return true;
    }
}
