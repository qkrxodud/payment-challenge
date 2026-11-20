package com.example.inventory.presentation.stock;

import com.example.inventory.application.stock.StockService;
import com.example.inventory.presentation.stock.dto.StockChangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 10장 출발 구조: 결제 서버가 부르는 재고 API. 줄이기는 재고가 모자라면 409(OUT_OF_STOCK)로 답한다.
 * 같은 요청이 두 번 오면 두 번 줄이거나 늘린다 — 어느 결제의 요청인지 받지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping("/stocks/{productId}/decrease")
    public ResponseEntity<Void> decrease(@PathVariable Long productId, @RequestBody StockChangeRequest request) {
        stockService.decrease(productId, request.quantity());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stocks/{productId}/increase")
    public ResponseEntity<Void> increase(@PathVariable Long productId, @RequestBody StockChangeRequest request) {
        stockService.increase(productId, request.quantity());
        return ResponseEntity.ok().build();
    }
}
