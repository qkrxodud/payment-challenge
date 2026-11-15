package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentCancel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 취소 한 건을 취소 결과로 옮긴다(5장). 남은 금액은 결제의 지금 남은 금액이 아니라 그 취소가 끝났을 때 남긴 금액이다 —
 * 같은 키로 다시 온 요청은 그사이 다른 부분 취소가 있었어도 처음과 같은 답을 받는다
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CancelResults {

    static CancelResult of(Payment payment, PaymentCancel cancel) {
        return new CancelResult(payment.getOrderId(), cancel.getAmount(), cancel.getStatus(), cancel.getBalanceAfter());
    }
}
