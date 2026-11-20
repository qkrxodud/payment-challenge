package com.example.payment.application.payment.comparison.dto;

/** 비교 구현(10장 결정 4, 조회로 확인): 재고 서비스가 돌려주는 예약 번호와 지금 상태 */
public record ReservationView(Long reservationId, String status) {
}
