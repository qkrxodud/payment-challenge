package com.example.inventory.domain.error.exception;

import com.example.inventory.domain.error.ErrorCode;
import com.example.inventory.domain.error.InventoryException;

/** 10장 결정 3: 이미 해제된 예약 번호로 예약이 왔다(해제가 먼저 도착함). 받지 않는다 */
public class ReservationReleasedException extends InventoryException {

    public ReservationReleasedException(Long reservationId) {
        super(ErrorCode.RESERVATION_RELEASED, "Reservation " + reservationId + " was already released.");
    }
}
