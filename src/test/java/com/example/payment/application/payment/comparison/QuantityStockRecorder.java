package com.example.payment.application.payment.comparison;

import com.example.inventory.domain.error.exception.OutOfStockException;
import com.example.inventory.domain.stock.StockLine;
import com.example.inventory.domain.stock.StockRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 구현(10장 결정 3, 수량만)의 재고 서비스 쪽: 예약 기록 없이 재고 수량만 바로 줄이고 늘린다(결정 1까지의 재고 서비스).
 * 결제 하나에 담긴 상품을 한 트랜잭션에서 상품 번호 순으로 줄이고, 하나라도 모자라면 아무것도 줄이지 않는다.
 * 재고 서비스에 끼우는 시험 구성품이다(시험 설정 test.inventory.components).
 */
@RequiredArgsConstructor
public class QuantityStockRecorder {

    private final StockRepository stockRepository;

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
