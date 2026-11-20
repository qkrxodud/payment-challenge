package com.example.inventory.infrastructure.persistence.stock;

import com.example.inventory.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 8장 결정 3을 그대로 옮겼다: 재고는 조건부 UPDATE 한 문장으로 줄인다 — 남은 수량이 줄일 수량 이상인 때만 바뀐다.
 * 바뀐 줄이 없으면 재고가 모자란 것이다.
 */
@Repository
@RequiredArgsConstructor
public class StockRepositoryAdapter implements StockRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean decrease(Long productId, int quantity) {
        return jdbcTemplate.update("update stock set quantity = quantity - ? where product_id = ? and quantity >= ?",
                quantity, productId, quantity) == 1;
    }

    @Override
    public void increase(Long productId, int quantity) {
        jdbcTemplate.update("update stock set quantity = quantity + ? where product_id = ?", quantity, productId);
    }
}
