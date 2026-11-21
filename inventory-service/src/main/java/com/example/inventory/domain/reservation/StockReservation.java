package com.example.inventory.domain.reservation;

import com.example.inventory.domain.stock.StockLine;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 10장 결정 3: 결제 하나가 잡은 재고(예약). 예약 번호는 결제 번호다. 상태 전이로 같은 확정·해제가 두 번 와도 한 번만 반영한다 —
 * 일반 중복 가리기(받은 메시지 번호를 남겨 거르기)가 아니라 예약 상태가 이미 지나갔는지를 보는 규칙이다.
 * 11장 결정 2: 예약은 마지막으로 반영한 재고 명령의 번호(결제 서버 payment_outbox 번호)를 쥔다. 그보다 번호가 크지 않은 명령은 반영하지 않는다 —
 * 같은 결제의 명령은 결제 줄을 잠근 기록이 차례로 남겨 번호가 만든 순서대로 커진다(9장). HTTP 로 잡은 예약에는 아직 번호가 없다.
 */
@Getter
public class StockReservation {

    private final Long reservationId;
    private final List<StockLine> items;
    private ReservationStatus status;
    // 11장 결정 2: 마지막으로 반영한 재고 명령의 번호. 명령을 아직 반영하지 않았으면 비어 있다
    private Long lastCommandId;
    private Instant updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private StockReservation(Long reservationId, List<StockLine> items, ReservationStatus status, Long lastCommandId,
                             Instant updatedAt) {
        this.reservationId = reservationId;
        this.items = List.copyOf(items);
        this.status = status;
        this.lastCommandId = lastCommandId;
        this.updatedAt = updatedAt;
    }

    // 담긴 상품의 재고를 잡은 예약
    public static StockReservation reserve(Long reservationId, List<StockLine> items, Instant now) {
        return of(reservationId, items, ReservationStatus.RESERVED, null, now);
    }

    // 예약보다 먼저 온 해제가 남기는 줄. 잡은 재고가 없고, 같은 번호로 늦게 온 예약을 막는다
    public static StockReservation releasedBeforeReserved(Long reservationId, long commandId, Instant now) {
        return of(reservationId, List.of(), ReservationStatus.RELEASED, commandId, now);
    }

    // 저장해 둔 예약을 다시 읽는다
    public static StockReservation of(Long reservationId, List<StockLine> items, ReservationStatus status, Long lastCommandId,
                                      Instant updatedAt) {
        return StockReservation.builder()
                .reservationId(reservationId)
                .items(items)
                .status(status)
                .lastCommandId(lastCommandId)
                .updatedAt(updatedAt)
                .build();
    }

    public boolean isReleased() {
        return status == ReservationStatus.RELEASED;
    }

    // 11장 결정 2: 마지막으로 반영한 명령보다 뒤에 만든 명령인가(번호가 더 큰가). 아직 명령을 반영하지 않은 예약이면 어느 명령이든 새 명령이다
    public boolean isAfterLastCommand(long commandId) {
        return Objects.isNull(lastCommandId) || commandId > lastCommandId;
    }

    // 잡아 둔 예약이면 확정하고 맞다고 답한다. 이미 확정·해제된 예약이면 바꾸지 않는다. 옛 명령인지(isAfterLastCommand)는 부르는 쪽이 먼저 본다
    public boolean confirm(long commandId, Instant now) {
        if (status.canConfirm()) {
            changeStatus(ReservationStatus.CONFIRMED, commandId, now);
            return true;
        }
        return false;
    }

    // 재고를 쥔 예약(잡음·확정)이면 해제하고 맞다고 답한다 — 잡은 재고를 돌려줄 때다. 이미 해제된 예약이면 바꾸지 않는다
    public boolean release(long commandId, Instant now) {
        if (status.holdsStock()) {
            changeStatus(ReservationStatus.RELEASED, commandId, now);
            return true;
        }
        return false;
    }

    private void changeStatus(ReservationStatus next, long commandId, Instant now) {
        this.status = next;
        this.lastCommandId = commandId;
        this.updatedAt = now;
    }
}
