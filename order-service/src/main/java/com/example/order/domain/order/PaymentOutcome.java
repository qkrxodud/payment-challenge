package com.example.order.domain.order;

import com.example.order.domain.error.exception.UnknownPaymentStatusException;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 9장: 결제 서버가 알려 오는 결제 결과(결제 상태 이름 그대로)와, 그 결과가 되면 주문이 갈 상태.
 * 모르는 결제 상태는 받지 않는다. 11장 결정 3: 모르는 상태는 다시 시도해도 같아(영구 오류) UnknownPaymentStatusException 으로 알린다.
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

    // 결제 서버가 알려 온 결제 상태 이름. eventId 는 알리지 못할 때 로그에 남길 이벤트 고유 번호다
    public static PaymentOutcome from(String status, String eventId) {
        return Arrays.stream(values())
                .filter(outcome -> outcome.name().equals(status))
                .findFirst()
                .orElseThrow(() -> new UnknownPaymentStatusException(eventId));
    }
}
