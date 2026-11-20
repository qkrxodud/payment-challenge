package com.example.payment.infrastructure.persistence.stock.comparison;

import com.example.inventory.domain.stock.StockRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 비교 구현(8장 결정 3): select … for update 로 재고 줄을 잠그고 읽은 뒤 확인하고 줄인다. 잠금은 인증 트랜잭션이 끝날 때까지 쥔다
 * (같은 트랜잭션에서 쿠폰·포인트를 잡고 인증을 남기는 동안에도).
 * 10장부터 재고 서비스 안의 재고 저장소를 바꿔 끼운다(시험 설정 test.inventory.components). 잠금은 재고 서비스의 기록 트랜잭션이 끝날 때까지 쥔다.
 */
@Primary
public class ForUpdateStockRepository implements StockRepository {

    private final JdbcTemplate jdbcTemplate;

    public ForUpdateStockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean decrease(Long productId, int quantity) {
        Integer current = jdbcTemplate.queryForObject("select quantity from stock where product_id = ? for update",
                Integer.class, productId);
        if (current < quantity) {
            return false;
        }
        jdbcTemplate.update("update stock set quantity = quantity - ? where product_id = ?", quantity, productId);
        return true;
    }

    // 8장 결정 4부터: 돌려주기는 운영과 같다
    @Override
    public void increase(Long productId, int quantity) {
        jdbcTemplate.update("update stock set quantity = quantity + ? where product_id = ?", quantity, productId);
    }
}
