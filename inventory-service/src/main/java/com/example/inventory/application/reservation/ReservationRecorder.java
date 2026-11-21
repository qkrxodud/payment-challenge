package com.example.inventory.application.reservation;

import com.example.inventory.domain.error.exception.OutOfStockException;
import com.example.inventory.domain.error.exception.ReservationReleasedException;
import com.example.inventory.domain.reservation.StockReservation;
import com.example.inventory.domain.reservation.StockReservationRepository;
import com.example.inventory.domain.stock.StockLine;
import com.example.inventory.domain.stock.StockRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 10장 결정 3: 예약을 남기고 바꾸는 짧은 트랜잭션. ReservationService 와 다른 빈으로 둔 이유: 같은 클래스 안에서 부르면 @Transactional 이
 * 적용되지 않는다. 재고 수량은 8장처럼 조건부 UPDATE 로 줄여 넘겨 팔지 않는다(상품 번호 순).
 */
@Component
@RequiredArgsConstructor
public class ReservationRecorder {

    private final StockReservationRepository stockReservationRepository;
    private final StockRepository stockRepository;
    private final Clock clock;

    // 예약 줄을 먼저 남기고 담긴 상품의 재고를 줄인다. 하나라도 모자라면 재고 없음으로 알려 예약 줄째 되돌린다.
    // 같은 번호의 줄이 이미 있으면: 해제된 줄(해제가 먼저 도착함)이면 받지 않고, 잡혀 있거나 확정된 줄이면 다시 온 예약으로 보고 그대로 둔다
    @Transactional
    public void reserve(Long reservationId, List<StockLine> items) {
        if (stockReservationRepository.claim(StockReservation.reserve(reservationId, items, clock.instant()))) {
            sorted(items).forEach(this::decrease);
            return;
        }
        StockReservation existing = stockReservationRepository.findForUpdate(reservationId).orElseThrow();
        if (existing.isReleased()) {
            throw new ReservationReleasedException(reservationId);
        }
    }

    private void decrease(StockLine line) {
        if (stockRepository.decrease(line.getProductId(), line.getQuantity())) {
            return;
        }
        throw new OutOfStockException(line.getProductId());
    }

    // 잡아 둔 예약을 확정한다. 이미 확정·해제됐거나 없는 예약이면 아무것도 바꾸지 않는다.
    // 11장 결정 2: 이 예약에 마지막으로 반영한 명령보다 먼저 만든 명령(commandId 가 더 작음)이면 아무것도 바꾸지 않는다
    @Transactional
    public void confirm(Long reservationId, long commandId) {
        Instant now = clock.instant();
        stockReservationRepository.findForUpdate(reservationId)
                .filter(reservation -> reservation.isAfterLastCommand(commandId))
                .filter(reservation -> reservation.confirm(commandId, now))
                .ifPresent(stockReservationRepository::updateStatus);
    }

    // 예약을 해제하고 잡은 재고를 돌려준다. 예약이 아직 없으면 해제된 줄을 먼저 남긴다(늦게 온 예약을 막는다).
    // 이미 해제된 예약이거나, 마지막으로 반영한 명령보다 먼저 만든 명령이면(11장 결정 2) 아무것도 바꾸지 않는다
    @Transactional
    public void release(Long reservationId, long commandId) {
        Instant now = clock.instant();
        if (stockReservationRepository.claim(StockReservation.releasedBeforeReserved(reservationId, commandId, now))) {
            return;
        }
        stockReservationRepository.findForUpdate(reservationId)
                .filter(reservation -> reservation.isAfterLastCommand(commandId))
                .filter(reservation -> reservation.release(commandId, now))
                .ifPresent(this::returnStock);
    }

    private void returnStock(StockReservation reservation) {
        stockReservationRepository.updateStatus(reservation);
        sorted(reservation.getItems()).forEach(line -> stockRepository.increase(line.getProductId(), line.getQuantity()));
    }

    private static List<StockLine> sorted(List<StockLine> lines) {
        return lines.stream()
                .sorted(Comparator.comparing(StockLine::getProductId))
                .toList();
    }
}
