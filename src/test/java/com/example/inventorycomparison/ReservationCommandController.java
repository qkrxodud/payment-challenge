package com.example.inventorycomparison;

import com.example.inventory.application.reservation.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비교 구현(10장 결정 5, 그 자리에서 HTTP)의 재고 서비스 쪽 API: 예약 확정·해제를 HTTP 로 받는다(결정 3~4 의 재고 서비스 API).
 * 상태 전이는 운영과 같다(몇 번 와도 한 번만 반영). 결제 서버 시험이 훑는 패키지(com.example.payment) 밖에 둔다.
 */
@RestController
@RequiredArgsConstructor
public class ReservationCommandController {

    private final ReservationService reservationService;

    @PostMapping("/reservations/{reservationId}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long reservationId) {
        reservationService.confirm(reservationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reservations/{reservationId}/release")
    public ResponseEntity<Void> release(@PathVariable Long reservationId) {
        reservationService.release(reservationId);
        return ResponseEntity.ok().build();
    }
}
