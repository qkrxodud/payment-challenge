package com.example.payment.domain.reconciliation;

/**
 * 12장 결정 1·2: 대사에서 우리 결제와 카드사 결제를 견주는 단계. 승인 결과를 기다림 → 승인 → 부분 취소 → 취소 순으로 나아간다.
 * 승인 없이 끝난 결제(준비, 인증 실패, 승인 실패, 만료)는 이 줄에 서지 않는다(NO_APPROVAL)
 */
public enum SettlementStage {
    NO_APPROVAL,
    AWAITING,
    APPROVED,
    PARTIALLY_CANCELED,
    CANCELED;

    public boolean isOnTrack() {
        return this != NO_APPROVAL;
    }

    // 승인이 한 번은 있었던 단계(승인, 부분 취소, 취소). 카드사 거래 내역에 승인 거래가 있어야 하는 결제다
    public boolean hadApproval() {
        return this == APPROVED || this == PARTIALLY_CANCELED || this == CANCELED;
    }

    public boolean hadNoApproval() {
        return this == NO_APPROVAL || this == AWAITING;
    }

    public boolean isBehind(SettlementStage other) {
        return isOnTrack() && other.isOnTrack() && ordinal() < other.ordinal();
    }
}
