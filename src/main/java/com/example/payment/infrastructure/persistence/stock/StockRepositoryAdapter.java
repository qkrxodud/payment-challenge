package com.example.payment.infrastructure.persistence.stock;

import com.example.payment.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockRepositoryAdapter implements StockRepository {

    private final JdbcTemplate jdbcTemplate;

    // 8장 결정 2: 지금 재고를 읽고, 모자라면 줄이지 않는다. 있으면 거기서 수량을 뺀 값을 쓴다(잠그지 않음 — 결정 3 에서 바꾼다)
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
}
