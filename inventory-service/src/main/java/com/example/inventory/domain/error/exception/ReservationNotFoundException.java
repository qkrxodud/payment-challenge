package com.example.inventory.domain.error.exception;

import com.example.inventory.domain.error.ErrorCode;
import com.example.inventory.domain.error.InventoryException;

/** 10장: 이 번호의 예약이 없다 */
public class ReservationNotFoundException extends InventoryException {

    public ReservationNotFoundException(Long reservationId) {
        super(ErrorCode.RESERVATION_NOT_FOUND, "Reservation " + reservationId + " was not found.");
    }
}
