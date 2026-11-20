package com.example.payment.infrastructure.persistence.stock.comparison;

import com.example.inventory.application.stock.StockRecorder;
import com.example.inventory.application.stock.StockService;
import com.example.inventory.domain.stock.StockLine;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Primary;

/**
 * 비교 구현(8장 결정 3, 변경 번호 + 다시 시도): 재고 변경 번호가 어긋나면 재고를 줄이는 기록 트랜잭션을 처음부터 다시 한다.
 * 다시 해도 어긋나기를 MAX_ATTEMPTS 번 하면 그 문제를 그대로 알린다(내부 오류).
 * 10장부터 재고 서비스 안에서 다시 한다(시험 설정 test.inventory.components). 8장에는 결제 서버의 승인 서비스가 인증부터 다시 했다.
 */
@Primary
public class RetryOnStockChangeStockService extends StockService {

    public static final int MAX_ATTEMPTS = 30;

    // 측정용: 변경 번호가 어긋나 다시 시도한 횟수. 재고 서비스는 시험 서버와 다른 컨텍스트에 떠 있어 시험이 읽을 수 있게 정적으로 둔다
    private static final AtomicInteger RETRIES = new AtomicInteger();

    public RetryOnStockChangeStockService(StockRecorder stockRecorder) {
        super(stockRecorder);
    }

    @Override
    public void decrease(List<StockLine> lines) {
        StockChangedException lastChange = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                super.decrease(lines);
                return;
            } catch (StockChangedException changed) {
                RETRIES.incrementAndGet();
                lastChange = changed;
            }
        }
        throw lastChange;
    }

    public static int takeRetries() {
        return RETRIES.getAndSet(0);
    }
}
