package com.example.inventory.application.stock;

import com.example.inventory.domain.stock.StockLine;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 서버가 부르는 재고 줄이기·늘리기. 기록은 StockRecorder 가 짧은 트랜잭션으로 한다(다른 빈으로 두어 @Transactional 이 적용되게 한다).
 * 10장 결정 1: 결제 하나에 담긴 상품의 재고를 한 번에(한 트랜잭션에서) 줄인다 — 하나라도 모자라면 아무것도 줄이지 않는다.
 * 누가(어느 결제가) 줄였는지는 남기지 않는다 — 수량만 바꾼다.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRecorder stockRecorder;

    public void decrease(List<StockLine> lines) {
        stockRecorder.decrease(lines);
    }

    public void increase(List<StockLine> lines) {
        stockRecorder.increase(lines);
    }
}
