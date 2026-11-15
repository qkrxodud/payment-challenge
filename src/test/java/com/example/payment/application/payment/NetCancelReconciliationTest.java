package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.NetCancelReconciler;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

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
