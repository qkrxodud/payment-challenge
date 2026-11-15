package com.example.payment.domain.payment;

/** 카드사에 결제 결과를 물어본 답(4장): 승인이 남았다, 승인이 없다, 카드사도 아직 처리 중이다 */
public enum PaymentInquiry {
    APPROVED,
    NOT_APPROVED,
    UNDECIDED;

    public boolean isApproved() {
        return this == APPROVED;
    }

    public boolean isNotApproved() {
        return this == NOT_APPROVED;
    }
}
