package com.example.payment.infrastructure.persistence.stock.comparison;

import com.example.inventory.domain.stock.StockRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 비교 구현(8장 결정 3): 잠그지 않음(결정 2까지). 지금 재고를 읽고, 모자라지 않으면 거기서 수량을 뺀 값을 쓴다.
 * 10장부터 재고 서비스 안의 재고 저장소를 바꿔 끼운다(시험 설정 test.inventory.components).
 */
@Primary
public class NoLockStockRepository implements StockRepository {

    private final JdbcTemplate jdbcTemplate;

    public NoLockStockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean decrease(Long productId, int quantity) {
        Integer current = jdbcTemplate.queryForObject("select quantity from stock where product_id = ?", Integer.class,
                productId);
        if (current < quantity) {
            return false;
        }
        jdbcTemplate.update("update stock set quantity = ? where product_id = ?", current - quantity, productId);
        return true;
    }

    // 8장 결정 4부터: 돌려주기는 운영과 같다
    @Override
    public void increase(Long productId, int quantity) {
        jdbcTemplate.update("update stock set quantity = quantity + ? where product_id = ?", quantity, productId);
    }
}
