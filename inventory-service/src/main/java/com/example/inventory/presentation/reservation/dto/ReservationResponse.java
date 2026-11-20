package com.example.inventory.presentation.reservation.dto;

import com.example.inventory.domain.reservation.StockReservation;

/** 10장: 예약 번호와 지금 상태 */
public record ReservationResponse(Long reservationId, String status) {

    public static ReservationResponse from(StockReservation reservation) {
        return new ReservationResponse(reservation.getReservationId(), reservation.getStatus().name());
    }
}
