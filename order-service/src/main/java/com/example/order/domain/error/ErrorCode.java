package com.example.order.domain.error;

/**
 * 11장 결정 3: 주문 서비스가 처리하지 못한 결제 이벤트의 이유. 주문 서비스는 웹 진입점이 없어 응답으로 바꾸지 않는다 —
 * 이 이유의 이벤트는 다시 시도해도 결과가 같아(영구 오류) 다시 시도하지 않는다.
 */
public enum ErrorCode {
    // 결제 서버가 알려 온 결제 상태를 모른다
    UNKNOWN_PAYMENT_STATUS,
    // 이벤트에 꼭 있어야 할 값(고유 번호, 주문번호)이 없거나 고유 번호가 숫자가 아니다
    INVALID_PAYMENT_EVENT
}
