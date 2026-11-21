package com.example.payment.application.payment.comparison;

import com.example.inventory.application.reservation.ReservationRecorder;
import com.example.inventory.domain.reservation.StockReservation;
import com.example.inventory.domain.reservation.StockReservationRepository;
import com.example.inventory.domain.stock.StockRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비교 구현(11장 결정 2, 거르지 않음 — 10장 재고 서비스): 예약마다 마지막 명령 번호를 보지 않고 예약 상태 전이(10장 결정 3)만으로 확정·해제한다.
 * 같은 명령은 결정 1대로 한 번만 처리한다. 재고 서비스에 끼우는 시험 구성품이다(test.inventory.components).
 */
@Primary
public class NoFilterReservationRecorder extends ReservationRecorder {

    private final StockReservationRepository reservations;
    private final StockRepository stocks;
    private final Clock clock;

    public NoFilterReservationRecorder(StockReservationRepository stockReservationRepository, StockRepository stockRepository,
                                       Clock clock) {
        super(stockReservationRepository, stockRepository, clock);
        this.reservations = stockReservationRepository;
        this.stocks = stockRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void confirm(Long reservationId, long commandId) {
        Instant now = clock.instant();
        reservations.findForUpdate(reservationId)
                .filter(reservation -> reservation.confirm(commandId, now))
                .ifPresent(reservations::updateStatus);
    }

    @Override
    @Transactional
    public void release(Long reservationId, long commandId) {
        Instant now = clock.instant();
        if (reservations.claim(StockReservation.releasedBeforeReserved(reservationId, commandId, now))) {
            return;
        }
        reservations.findForUpdate(reservationId)
                .filter(reservation -> reservation.release(commandId, now))
                .ifPresent(this::returnStock);
    }

    private void returnStock(StockReservation reservation) {
        reservations.updateStatus(reservation);
        reservation.getItems().forEach(line -> stocks.increase(line.getProductId(), line.getQuantity()));
    }
}
