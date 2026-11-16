package com.example.payment.domain.payment;

import java.util.Arrays;

/**
 * 카드사가 알려 준 결제 상태(6장). 이름은 토스페이먼츠 Payment 객체의 status 값을 따른다. 목록에 없는 값은 UNKNOWN 이다.
 * 카드사에 이 주문의 결제가 없을 때도 UNKNOWN 으로 본다.
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

    // 카드사가 한 번은 승인한 결제인가. 토스페이먼츠의 부분 취소·취소는 "승인된 결제가" 취소된 상태다
    public boolean hasApproval() {
        return this == DONE || this == PARTIAL_CANCELED || this == CANCELED;
    }

    // 카드사가 승인하지 않고 끝낸 결제인가(승인 실패, 만료)
    public boolean isDeclined() {
        return this == ABORTED || this == EXPIRED;
    }
}
