package com.example.payment.infrastructure.persistence.stock.comparison;

import com.example.inventory.domain.stock.StockRepository;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 비교 구현(8장 결정 3): 변경 번호(@Version 과 같은 방법). 재고와 변경 번호를 잠그지 않고 읽고, 확인한 뒤 "읽은 번호 그대로일 때만" 줄이며 번호를 올린다
 * (Hibernate 가 @Version 엔티티를 고칠 때 내는 update … where … and version = ? 와 같은 모양). 번호가 어긋나 바뀐 줄이 없으면
 * StockChangedException 으로 알린다 — 같은 트랜잭션 안에서 다시 읽으면 같은 스냅숏(REPEATABLE READ)을 다시 보므로,
 * 다시 시도는 기록 트랜잭션 밖에서 처음부터 다시 하는 재고 서비스(RetryOnStockChangeStockService)가 한다.
 * version 칸은 이 비교의 시험 DB 에만 더한다(stock-version.sql).
 * 10장부터 재고 서비스 안의 재고 저장소를 바꿔 끼운다(시험 설정 test.inventory.components). 8장에는 결제 서버의 승인 서비스가 인증부터 다시 했다.
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

    // 8장 결정 4부터: 돌려줄 때도 변경 번호를 올린다(같은 재고를 읽어 둔 다른 주문이 그 뒤 값을 덮어쓰지 않게)
    @Override
    public void increase(Long productId, int quantity) {
        jdbcTemplate.update("update stock set quantity = quantity + ?, version = version + 1 where product_id = ?", quantity,
                productId);
    }
}
