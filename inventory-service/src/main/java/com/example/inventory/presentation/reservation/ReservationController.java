package com.example.inventory.presentation.reservation;

import com.example.inventory.application.reservation.ReservationService;
import com.example.inventory.presentation.reservation.dto.ReservationResponse;
import com.example.inventory.presentation.reservation.dto.ReserveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 10장 결정 3: 재고 예약 API. 예약은 재고가 모자라면 409(OUT_OF_STOCK), 이미 해제된 예약 번호면 409(RESERVATION_RELEASED)로 답한다.
 * 같은 예약이 다시 오면 다시 잡지 않고 200 으로 답한다. 확정·해제는 몇 번 와도 한 번만 반영한다.
 */
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/reservations")
    public ResponseEntity<Void> reserve(@RequestBody ReserveRequest request) {
        reservationService.reserve(request.reservationId(), request.toLines());
        return ResponseEntity.ok().build();
    }

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

    @GetMapping("/reservations/{reservationId}")
    public ResponseEntity<ReservationResponse> find(@PathVariable Long reservationId) {
        return ResponseEntity.ok(ReservationResponse.from(reservationService.find(reservationId)));
    }
}
