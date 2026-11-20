package com.example.inventory.application.stock;

import com.example.inventory.domain.error.exception.OutOfStockException;
import com.example.inventory.domain.stock.StockLine;
import com.example.inventory.domain.stock.StockRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 10장: 재고를 바꾸는 짧은 트랜잭션. StockService 와 다른 빈으로 둔 이유: 같은 클래스 안에서 부르면 @Transactional 이 적용되지 않는다.
 * 상품 번호 순으로 바꾼다 — 여러 상품을 담은 결제끼리 서로의 줄을 기다리며 멈추지 않게 한다(8장과 같은 순서).
 */
@Component
@RequiredArgsConstructor
public class StockRecorder {

    private final StockRepository stockRepository;

    // 하나라도 모자라면 재고 없음으로 알려 트랜잭션째 되돌린다(앞서 줄인 상품도 남지 않는다)
    @Transactional
    public void decrease(List<StockLine> lines) {
        sorted(lines).forEach(this::decrease);
    }

    private void decrease(StockLine line) {
        if (stockRepository.decrease(line.getProductId(), line.getQuantity())) {
            return;
        }
        throw new OutOfStockException(line.getProductId());
    }

    @Transactional
    public void increase(List<StockLine> lines) {
        sorted(lines).forEach(line -> stockRepository.increase(line.getProductId(), line.getQuantity()));
    }

    private static List<StockLine> sorted(List<StockLine> lines) {
        return lines.stream()
                .sorted(Comparator.comparing(StockLine::getProductId))
                .toList();
    }
}
