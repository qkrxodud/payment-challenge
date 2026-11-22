package com.example.payment.domain.payment;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 12장 결정 3: 결제 상태 전이 한 번(이전 상태, 바꾼 행위, 다음 상태). 허용 전이 표(1장)의 한 칸이다 */
@Getter
public class StatusTransition {

    private final PaymentStatus from;
    private final PaymentAction action;
    private final PaymentStatus to;

    @Builder(access = AccessLevel.PRIVATE)
    private StatusTransition(PaymentStatus from, PaymentAction action, PaymentStatus to) {
        this.from = from;
        this.action = action;
        this.to = to;
    }

    public static StatusTransition of(PaymentStatus from, PaymentAction action, PaymentStatus to) {
        return StatusTransition.builder()
                .from(from)
                .action(action)
                .to(to)
                .build();
    }
}
