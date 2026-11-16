package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentRecorder;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.config.ReconciliationProperties;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;

/**
 * 비교 구현 D: 기록 실패를 요청 안에서 다시 시도. 카드사 승인 뒤 결과 기록이 실패하면 0.1초 쉬고 두 번 더 기록해 본다.
 * 그래도 실패하면 실패를 알린다. 보정은 결정 3까지처럼 결과 모름만 본다.
 */
@RequiredArgsConstructor
public class RetryRecordConfirmService {

    private static final int ATTEMPTS = 3;
    private static final long PAUSE_MILLIS = 100;

    private final PaymentRecorder paymentRecorder;
    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final ReconciliationProperties reconciliationProperties;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = paymentRecorder.authenticate(command);
        if (payment.isConfirmedWith(command.paymentKey())) {
            return PaymentResult.from(payment);
        }
        try {
            PaymentApproval approval = paymentGateway.approve(command.paymentKey(), payment.getOrderId(), payment.getAmount());
            return recordWithRetry(command.orderId(), approval, 1);
        } catch (PaymentGatewayNoResponseException noResponse) {
            paymentRecorder.recordApprovalUnknown(command.orderId());
            throw noResponse;
        }
    }

    private PaymentResult recordWithRetry(String orderId, PaymentApproval approval, int attempt) {
        try {
            return paymentRecorder.recordApproval(orderId, approval);
        } catch (DataAccessException recordFailed) {
            if (attempt >= ATTEMPTS) {
                throw recordFailed;
            }
            pause();
            return recordWithRetry(orderId, approval, attempt + 1);
        }
    }

    private static void pause() {
        try {
            Thread.sleep(PAUSE_MILLIS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public void reconcile(Instant now) {
        Instant requestedBefore = now.minus(reconciliationProperties.getWaitAfterRequest());
        paymentRepository.findApprovalRequestedBefore(Set.of(PaymentStatus.APPROVAL_UNKNOWN), requestedBefore)
                .forEach(unknown -> reconcile(unknown.getOrderId()));
    }

    // 운영 구현과 같이 조회에 답이 없으면 그대로 두고 다음 보정에서 다시 본다
    private void reconcile(String orderId) {
        try {
            paymentRecorder.recordInquiry(orderId, paymentGateway.findPayment(orderId));
        } catch (PaymentGatewayNoResponseException noResponse) {
            // 다음 보정에서 다시 본다
        }
    }
}
