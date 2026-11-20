package com.example.inventory.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.inventory.domain.stock.StockLine;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("재고 예약의 상태 전이")
class StockReservationTest {

    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");

    static StockReservation reserved() {
        return StockReservation.reserve(1L, List.of(StockLine.of(10L, 2)), NOW);
    }

    @Test
    @DisplayName("잡아 둔 예약은 확정된다")
    void reservedIsConfirmed() {
        StockReservation reservation = reserved();

        reservation.confirm(NOW);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("이미 확정된 예약에 다시 온 확정은 아무것도 바꾸지 않는다")
    void repeatedConfirmChangesNothing() {
        StockReservation reservation = reserved();
        reservation.confirm(NOW);

        assertThat(reservation.confirm(NOW.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("해제된 예약에 늦게 온 확정은 예약을 되살리지 않는다")
    void confirmAfterReleaseKeepsReleased() {
        StockReservation reservation = reserved();
        reservation.release(NOW);

        reservation.confirm(NOW.plusSeconds(1));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("잡아 둔 예약을 해제하면 잡은 재고를 돌려줄 때다")
    void releasingReservedReturnsStock() {
        assertThat(reserved().release(NOW)).isTrue();
    }

    @Test
    @DisplayName("확정된 예약도 결제가 취소되면 해제되고 잡은 재고를 돌려줄 때다")
    void releasingConfirmedReturnsStock() {
        StockReservation reservation = reserved();
        reservation.confirm(NOW);

        assertThat(reservation.release(NOW.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("이미 해제된 예약에 다시 온 해제는 재고를 다시 돌려주지 않는다")
    void repeatedReleaseReturnsNothing() {
        StockReservation reservation = reserved();
        reservation.release(NOW);

        assertThat(reservation.release(NOW.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("예약보다 먼저 온 해제가 남긴 줄은 잡은 재고 없이 해제된 상태다")
    void releaseBeforeReservationHoldsNothing() {
        StockReservation released = StockReservation.releasedBeforeReserved(1L, NOW);

        assertThat(List.of(released.isReleased(), released.getItems().isEmpty())).containsExactly(true, true);
    }
}
