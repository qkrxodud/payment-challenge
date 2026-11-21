package com.example.inventorycomparison;

import com.example.inventory.application.reservation.ReservationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비교 구현(10장 결정 5, 그 자리에서 HTTP)의 재고 서비스 쪽 API: 예약 확정·해제를 HTTP 로 받는다(결정 3~4 의 재고 서비스 API).
 * 상태 전이는 운영과 같다(몇 번 와도 한 번만 반영). 결제 서버 시험이 훑는 패키지(com.example.payment) 밖에 둔다.
 * 11장: HTTP 로 받는 확정·해제에는 명령 번호가 없어 처리한 명령 기록(결정 1)을 거치지 않고 예약 기록을 바로 부른다.
 * 예약 기록이 받는 명령 번호(결정 2)는 받은 순서대로 커지는 이 JVM 의 시각(nanoTime)으로 대신한다 — 받은 순서가 곧 만든 순서다(10장 비교 그대로).
 */
@RestController
@RequiredArgsConstructor
public class ReservationCommandController {

    private final ReservationRecorder reservationRecorder;

    @PostMapping("/reservations/{reservationId}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long reservationId) {
        reservationRecorder.confirm(reservationId, System.nanoTime());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reservations/{reservationId}/release")
    public ResponseEntity<Void> release(@PathVariable Long reservationId) {
        reservationRecorder.release(reservationId, System.nanoTime());
        return ResponseEntity.ok().build();
    }
}
