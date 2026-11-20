package com.example.inventorycomparison;

import com.example.inventory.domain.stock.StockLine;
import com.example.payment.application.payment.comparison.QuantityStockRecorder;
import com.example.payment.application.payment.comparison.dto.QuantityStockRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비교 구현(10장 결정 3, 수량만)의 재고 서비스 쪽 API: POST /stocks/decrease·increase(결정 1까지의 재고 API). 줄이기는 모자라면 409.
 * 같은 요청이 두 번 오면 두 번 줄이거나 늘린다.
 * 웹 진입점(@RestController)이라 결제 서버 시험이 훑는 패키지(com.example.payment) 밖에 둔다 — 결제 서버 시험 컨텍스트에 끼지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class QuantityStockController {

    private final QuantityStockRecorder quantityStockRecorder;

    @PostMapping("/stocks/decrease")
    public ResponseEntity<Void> decrease(@RequestBody QuantityStockRequest request) {
        quantityStockRecorder.decrease(linesOf(request));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stocks/increase")
    public ResponseEntity<Void> increase(@RequestBody QuantityStockRequest request) {
        quantityStockRecorder.increase(linesOf(request));
        return ResponseEntity.ok().build();
    }

    static List<StockLine> linesOf(QuantityStockRequest request) {
        return request.items().stream()
                .map(item -> StockLine.of(item.productId(), item.quantity()))
                .toList();
    }
}
