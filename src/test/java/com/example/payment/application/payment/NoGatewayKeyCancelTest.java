package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.infrastructure.pg.NoIdempotencyKeyPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("결제 취소 — 비교 D: 카드사에 멱등키를 넘기지 않음")
class NoGatewayKeyCancelTest extends CancelContract {

    @Autowired
    PaymentCancelRecorder paymentCancelRecorder;

    @Autowired
    PaymentCancelReconciler paymentCancelReconciler;

    @Autowired
    RestClient pgRestClient;

    PaymentCancelService service;

    // 운영 구현의 취소 서비스에 멱등키를 넘기지 않는 카드사 연결만 바꿔 끼운다
    @BeforeEach
    void connect() {
        service = new PaymentCancelService(paymentCancelRecorder, new NoIdempotencyKeyPaymentGateway(pgRestClient));
    }

    @Override
    CancelResult cancel(CancelPaymentCommand command) {
        return service.cancel(command);
    }

    @Override
    void reconcile() {
        paymentCancelReconciler.reconcile(afterWaiting());
    }
}
