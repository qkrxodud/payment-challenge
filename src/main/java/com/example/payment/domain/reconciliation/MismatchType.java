package com.example.payment.domain.reconciliation;

/** 12장 결정 1: 대사에서 찾은 어긋남의 종류 */
public enum MismatchType {
    // 우리 기록이 카드사보다 뒤처짐(카드사가 더 나아감 — 우리는 승인, 카드사는 취소 등)
    STATUS_BEHIND,
    // 그 밖에 상태가 다름(우리가 앞섬, 우리는 승인 없이 끝났는데 카드사는 승인 등)
    STATUS_MISMATCH,
    // 승인 금액이 다름
    AMOUNT_MISMATCH,
    // 우리에게만 있는 승인 결제(카드사 거래 내역에 없음)
    OURS_ONLY,
    // 카드사에만 있는 결제(우리에게 같은 결제 키의 결제가 없음)
    PG_ONLY
}
