package com.example.inventory.domain.reservation;

import com.example.inventory.domain.stock.StockLine;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 10장 결정 3: 결제 하나가 잡은 재고(예약). 예약 번호는 결제 번호다. 상태 전이로 같은 확정·해제가 두 번 와도 한 번만 반영한다 —
 * 일반 중복 가리기(받은 메시지 번호를 남겨 거르기)가 아니라 예약 상태가 이미 지나갔는지를 보는 규칙이다.
 */
@Getter
public class StockReservation {

    private final Long reservationId;
    private final List<StockLine> items;
    private ReservationStatus status;
    private Instant updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private StockReservation(Long reservationId, List<StockLine> items, ReservationStatus status, Instant updatedAt) {
        this.reservationId = reservationId;
        this.items = List.copyOf(items);
        this.status = status;
        this.updatedAt = updatedAt;
    }

    // 담긴 상품의 재고를 잡은 예약
    public static StockReservation reserve(Long reservationId, List<StockLine> items, Instant now) {
        return of(reservationId, items, ReservationStatus.RESERVED, now);
    }

    // 예약보다 먼저 온 해제가 남기는 줄. 잡은 재고가 없고, 같은 번호로 늦게 온 예약을 막는다
    public static StockReservation releasedBeforeReserved(Long reservationId, Instant now) {
        return of(reservationId, List.of(), ReservationStatus.RELEASED, now);
    }

    // 저장해 둔 예약을 다시 읽는다
    public static StockReservation of(Long reservationId, List<StockLine> items, ReservationStatus status, Instant updatedAt) {
        return StockReservation.builder()
                .reservationId(reservationId)
                .items(items)
                .status(status)
                .updatedAt(updatedAt)
                .build();
    }

    public boolean isReleased() {
        return status == ReservationStatus.RELEASED;
    }

    // 잡아 둔 예약이면 확정하고 맞다고 답한다. 이미 확정·해제된 예약이면 바꾸지 않는다
    public boolean confirm(Instant now) {
        if (status.canConfirm()) {
            changeStatus(ReservationStatus.CONFIRMED, now);
            return true;
        }
        return false;
    }

    // 재고를 쥔 예약(잡음·확정)이면 해제하고 맞다고 답한다 — 잡은 재고를 돌려줄 때다. 이미 해제된 예약이면 바꾸지 않는다
    public boolean release(Instant now) {
        if (status.holdsStock()) {
            changeStatus(ReservationStatus.RELEASED, now);
            return true;
        }
        return false;
    }

    private void changeStatus(ReservationStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }
}
