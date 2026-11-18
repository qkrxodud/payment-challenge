package com.example.payment.infrastructure.persistence.stock;

import com.example.payment.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 8장 결정 3: 재고는 조건부 UPDATE 한 문장으로 줄인다 — 남은 수량이 줄일 수량 이상인 때만 바뀐다. 바뀐 줄이 없으면 재고가 모자란 것이다.
 * 읽고 확인한 뒤 쓰면 그 사이 다른 주문이 같은 재고를 읽어 둘 다 줄였다(출발 구조, 결정 2).
 * 7장 결제 줄 잠금은 결제마다 줄이 달라 충돌이 적었지만, 재고 줄은 같은 상품의 모든 주문이 한 줄에 몰린다.
 * 이 문장도 바꾼 재고 줄의 잠금을 인증 트랜잭션이 끝날 때까지 쥔다(select … for update 와 같다). 측정은 docs/ch08/04.
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
