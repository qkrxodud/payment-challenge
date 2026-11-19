package com.example.order.domain.order;

/**
 * 9장: 주문 상태. 주문 서비스는 결제 이벤트로만 주문 상태를 바꾼다(주문을 만드는 일은 이 장의 범위 밖 — 결제 이벤트가 처음 오면 주문을 남긴다).
 */
public enum OrderStatus {
    PAID,            // 결제 완료
    PAYMENT_FAILED,  // 결제 실패(승인 거절·만료)
    CANCELED         // 결제 취소(승인 취소·매입 취소)
}
