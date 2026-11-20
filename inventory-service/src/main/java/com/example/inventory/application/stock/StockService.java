package com.example.inventory.application.stock;

import com.example.inventory.domain.error.exception.OutOfStockException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 10장 출발 구조: 결제 서버가 부르는 재고 줄이기·늘리기. 8장에 결제 서버 안에 있던 재고 저장소를 HTTP 뒤로 옮긴 것이다.
 * 누가(어느 결제가) 줄였는지는 남기지 않는다 — 수량만 바꾼다.
 * 기록은 StockRecorder 가 짧은 트랜잭션으로 한다(다른 빈으로 두어 @Transactional 이 적용되게 한다).
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRecorder stockRecorder;

    public void decrease(Long productId, int quantity) {
        if (stockRecorder.decrease(productId, quantity)) {
            return;
        }
        throw new OutOfStockException(productId);
    }

    public void increase(Long productId, int quantity) {
        stockRecorder.increase(productId, quantity);
    }
}
