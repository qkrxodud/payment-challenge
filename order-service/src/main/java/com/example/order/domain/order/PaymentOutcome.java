package com.example.order.domain.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 9장: 결제 서버가 알려 오는 결제 결과(결제 상태 이름 그대로)와, 그 결과가 되면 주문이 갈 상태.
 * 모르는 결제 상태는 받지 않는다(valueOf 가 거부한다).
 */
@Getter
@RequiredArgsConstructor
public enum PaymentOutcome {
    APPROVED(OrderStatus.PAID),
    CAPTURED(OrderStatus.PAID),
    APPROVAL_FAILED(OrderStatus.PAYMENT_FAILED),
    EXPIRED(OrderStatus.PAYMENT_FAILED),
    APPROVAL_CANCELED(OrderStatus.CANCELED),
    CAPTURE_CANCELED(OrderStatus.CANCELED);

    private final OrderStatus orderStatus;
}
