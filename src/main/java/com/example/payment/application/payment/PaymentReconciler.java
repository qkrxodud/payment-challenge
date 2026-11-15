package com.example.payment.application.payment;

import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.config.ReconciliationProperties;
import com.example.payment.global.error.exception.PaymentGatewayFailedException;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 결과 모름 결제와 결과를 기록하지 못하고 인증 상태에 남은 결제를 카드사 기록에 맞춘다(4장). 주문번호로 카드사에 결제를 조회하고 그 결과대로 승인 또는 승인 실패로 닫는다.
 * 카드사에 기록이 없으면 승인 요청이 닿지 않은 것으로 보고 승인 실패로 닫는다.
 * 조회에도 답이 없으면 상태를 바꾸지 않는다. 결과 모름으로 남아 있으니 다음 보정에서 다시 본다.
 * 승인을 요청한 지 정한 시간(payment.reconciliation.wait-after-request)이 지난 결제만 본다.
 * 카드사가 아직 처리 중인 결제를 조회하면 기록이 없다는 답을 받아 승인 실패로 잘못 닫을 수 있어서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciler {

    // 보정 대상 상태. 카드사 승인 뒤 결과 기록이 실패하면 결과 모름조차 남지 않고 인증 상태에 머무르므로 함께 본다.
    // 비교 구현(망취소)도 같은 대상을 본다
    public static final Set<PaymentStatus> TARGETS = Set.of(PaymentStatus.APPROVAL_UNKNOWN, PaymentStatus.AUTHENTICATED);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentRecorder paymentRecorder;
    private final ReconciliationProperties reconciliationProperties;

    public void reconcile(Instant now) {
        Instant requestedBefore = now.minus(reconciliationProperties.getWaitAfterRequest());
        paymentRepository.findApprovalRequestedBefore(TARGETS, requestedBefore)
                .forEach(payment -> reconcile(payment.getOrderId()));
    }

    // 결제 하나를 맞추다 실패해도 나머지 결제는 이어서 맞춘다
    private void reconcile(String orderId) {
        try {
            paymentRecorder.recordInquiry(orderId, paymentGateway.inquire(orderId));
        } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException unanswered) {
            log.warn("Could not confirm a payment result with the gateway ({}). It will be checked again next time.",
                    unanswered.getClass().getSimpleName());
        }
    }
}
