package com.example.payment.domain.reconciliation;

/** 12장 결정 1·2: 대사가 어긋남을 어떻게 처리했는가 */
public enum Resolution {
    // 대사가 카드사를 따라 맞췄다(6장 따라가기)
    FOLLOWED,
    // 운영자가 확인할 것
    NEEDS_REVIEW
}
