package com.example.payment.domain.payment;

import java.util.Arrays;
import java.util.Optional;

/**
 * 카드사가 알려 준 결제 상태(6장). 이름은 토스페이먼츠 Payment 객체의 status 값을 따른다. 목록에 없는 값은 UNKNOWN 이다.
 */
public enum GatewayStatus {
    READY,
    IN_PROGRESS,
    WAITING_FOR_DEPOSIT,
    DONE,
    PARTIAL_CANCELED,
    CANCELED,
    ABORTED,
    EXPIRED,
    UNKNOWN;

    public static GatewayStatus of(String status) {
        return Arrays.stream(values())
                .filter(known -> known.name().equals(status))
                .findFirst()
                .orElse(UNKNOWN);
    }

    // 이 상태를 우리 결제에 옮길 행위. 승인은 승인, 거절·만료는 승인 실패, 전액 취소는 취소다. 부분 취소는 옮기지 않는다
    public Optional<PaymentAction> reportedAction() {
        return switch (this) {
            case DONE -> Optional.of(PaymentAction.APPROVE);
            case ABORTED, EXPIRED -> Optional.of(PaymentAction.FAIL_APPROVAL);
            case CANCELED -> Optional.of(PaymentAction.CANCEL);
            default -> Optional.empty();
        };
    }
}
