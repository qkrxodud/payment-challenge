package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NetCancelReconciler;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 6장부터 이 비교 구현은 계약 일부를 지키지 못한다. 카드사 쪽 승인 뒤 취소(CANCELED)의 짝을 승인 실패에서 승인 취소로 바꿨기 때문이다 —
 * 망취소는 카드사를 CANCELED 로 만들고 우리 결제는 승인 실패로 닫는다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다
 */
@Tag("known-failing")
@Import(NetCancelReconciler.class)
@DisplayName("결과 모름 보정 — 비교 A: 망취소(카드사에 취소를 보내고 승인 실패로 닫음)")
class NetCancelReconciliationTest extends ReconciliationContract {

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    NetCancelReconciler netCancelReconciler;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return paymentConfirmService.confirm(command);
    }

    @Override
    void reconcile() {
        netCancelReconciler.reconcile(afterWaiting());
    }
}
