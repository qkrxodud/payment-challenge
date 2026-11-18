package com.example.payment.infrastructure.persistence.stock;

import com.example.payment.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockRepositoryAdapter implements StockRepository {

    private final JdbcTemplate jdbcTemplate;

    // 출발 구조: 지금 재고를 읽고, 거기서 수량을 뺀 값을 쓴다
    @Override
    public void decrease(Long productId, int quantity) {
        Integer current = jdbcTemplate.queryForObject("select quantity from stock where product_id = ?", Integer.class,
                productId);
        jdbcTemplate.update("update stock set quantity = ? where product_id = ?", current - quantity, productId);
    }
}
