package com.example.inventory.domain.reservation;

import java.util.Optional;

/** 10장 결정 3: 예약 기록 */
public interface StockReservationRepository {

    // 이 번호의 예약이 아직 없으면 남기고 맞다고 답한다. 이미 있으면(다른 트랜잭션이 남기는 중이면 끝날 때까지 기다린 뒤) 아니라고 답한다
    boolean claim(StockReservation reservation);

    // 예약을 잠그고 읽는다
    Optional<StockReservation> findForUpdate(Long reservationId);

    Optional<StockReservation> findById(Long reservationId);

    void updateStatus(StockReservation reservation);
}
