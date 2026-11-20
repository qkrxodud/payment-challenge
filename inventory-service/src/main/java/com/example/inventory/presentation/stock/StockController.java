package com.example.inventory.presentation.stock;

import com.example.inventory.application.stock.StockService;
import com.example.inventory.presentation.stock.dto.StockChangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 서버가 부르는 재고 API. 줄이기는 재고가 모자라면 409(OUT_OF_STOCK)로 답한다.
 * 10장 결정 1: 결제 하나에 담긴 상품을 한 요청으로 받아 한꺼번에 줄인다(하나라도 모자라면 아무것도 줄이지 않음).
 * 같은 요청이 두 번 오면 두 번 줄이거나 늘린다 — 어느 결제의 요청인지 받지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping("/stocks/decrease")
    public ResponseEntity<Void> decrease(@RequestBody StockChangeRequest request) {
        stockService.decrease(request.toLines());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stocks/increase")
    public ResponseEntity<Void> increase(@RequestBody StockChangeRequest request) {
        stockService.increase(request.toLines());
        return ResponseEntity.ok().build();
    }
}
