package com.example.inventory.domain.reservation;

/** 10장 결정 3: 예약 상태. RESERVED → CONFIRMED / RELEASED, CONFIRMED → RELEASED(승인 뒤 전액 취소). RELEASED 는 끝이다 */
public enum ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED;

    // 확정할 수 있는 상태인가(잡아 둔 예약만). 이미 확정·해제된 예약에 다시 온 확정은 아무것도 바꾸지 않는다
    public boolean canConfirm() {
        return this == RESERVED;
    }

    // 돌려줄 재고를 쥐고 있는 상태인가(잡음, 확정). 이미 해제된 예약에 다시 온 해제는 아무것도 바꾸지 않는다
    public boolean holdsStock() {
        return this == RESERVED || this == CONFIRMED;
    }
}
