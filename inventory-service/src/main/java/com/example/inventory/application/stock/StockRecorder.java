package com.example.inventory.application.stock;

import com.example.inventory.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 10장: 재고를 바꾸는 짧은 트랜잭션. StockService 와 다른 빈으로 둔 이유: 같은 클래스 안에서 부르면 @Transactional 이 적용되지 않는다 */
@Component
@RequiredArgsConstructor
public class StockRecorder {

    private final StockRepository stockRepository;

    @Transactional
    public boolean decrease(Long productId, int quantity) {
        return stockRepository.decrease(productId, quantity);
    }

    @Transactional
    public void increase(Long productId, int quantity) {
        stockRepository.increase(productId, quantity);
    }
}
