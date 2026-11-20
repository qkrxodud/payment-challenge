package com.example.payment.application.payment;

import com.example.payment.domain.error.exception.PaymentGatewayFailedException;
import com.example.payment.domain.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.domain.payment.CancelStatus;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.config.ReconciliationProperties;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 결과를 모르는 취소와, 카드사 답을 기록하지 못하고 요청 상태에 남은 취소를 카드사 기록에 맞춘다(5장 결정 4).
 * 4장 승인 보정을 취소로 넓혔다. 주문번호로 카드사에 결제를 조회해 취소 이력(cancels)으로 닫는다.
 * 취소를 요청한 지 정한 시간(payment.reconciliation.wait-after-request)이 지난 취소만 본다 — 카드사가 아직 처리 중인 취소는
 * 이력에 없어서 취소 실패로 잘못 닫을 수 있다. 조회에 답이 없으면 그대로 두고 다음 보정에서 다시 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCancelReconciler {

    private static final Set<CancelStatus> TARGETS = Set.of(CancelStatus.UNKNOWN, CancelStatus.REQUESTED);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentCancelRecorder paymentCancelRecorder;
    private final ReconciliationProperties reconciliationProperties;

    public void reconcile(Instant now) {
        Instant requestedBefore = now.minus(reconciliationProperties.getWaitAfterRequest());
        paymentRepository.findCancelRequestedBefore(TARGETS, requestedBefore)
                .forEach(payment -> reconcile(payment.getOrderId(), requestedBefore));
    }

    // 결제 하나를 맞추다 실패해도 나머지 결제는 이어서 맞춘다
    private void reconcile(String orderId, Instant requestedBefore) {
        try {
            paymentCancelRecorder.recordInquiry(orderId, paymentGateway.findCancels(orderId), requestedBefore);
        } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException unanswered) {
            log.warn("Could not confirm cancels with the gateway ({}). They will be checked again next time.",
                    unanswered.getClass().getSimpleName());
        }
    }
}
