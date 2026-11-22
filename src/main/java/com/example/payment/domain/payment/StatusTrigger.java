package com.example.payment.domain.payment;

/** 12장 결정 3: 결제 상태를 바꾼 길(계기). 같은 전이라도 어느 길로 왔는지가 대사에서 어긋난 결제를 되짚는 실마리다 */
public enum StatusTrigger {
    // 고객의 승인 요청(인증, 승인, 거절, 결과 모름, 재고 없음으로 닫음)
    CONFIRM,
    // 4장 결과 모름 보정
    APPROVAL_RECONCILIATION,
    // 6장 카드사 알림을 받고 따라감
    WEBHOOK,
    // 고객의 취소 요청
    CANCEL,
    // 5장 결과를 기다리는 취소 보정
    CANCEL_RECONCILIATION,
    // 12장 하루치 대사가 따라감
    DAILY_RECONCILIATION
}
