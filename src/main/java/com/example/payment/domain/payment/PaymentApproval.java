package com.example.payment.domain.payment;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** PG 승인 요청의 결과: 승인 또는 거절(거절 사유 코드 포함) */
@Getter
public class PaymentApproval {

    private final boolean declined;
    private final String declineCode;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentApproval(boolean declined, String declineCode) {
        this.declined = declined;
        this.declineCode = declineCode;
    }

    public static PaymentApproval approved() {
        return PaymentApproval.builder()
                .declined(false)
                .build();
    }

    public static PaymentApproval declined(String declineCode) {
        return PaymentApproval.builder()
                .declined(true)
                .declineCode(declineCode)
                .build();
    }
}
