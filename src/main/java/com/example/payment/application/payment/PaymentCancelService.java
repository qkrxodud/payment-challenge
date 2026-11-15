package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelAttempt;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.payment.CancelOutcome;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 취소(5장). 취소마다 한 건을 남긴다(결정 1).
 * 승인이 남은 결제인지, 더 취소할 수 있는 금액 안의 취소인지 확인한다. 금액이 없으면 그 금액 전부를 취소한다(결정 2).
 * 같은 Idempotency-Key 로 다시 온 요청은 카드사를 다시 부르지 않고 남긴 결과로 답한다. 키는 반드시 받는다(결정 3).
 * 취소 요청을 먼저 기록하고(트랜잭션), 카드사 호출은 트랜잭션 밖에서 한 뒤, 답을 따로 기록한다.
 * 카드사 답을 받지 못하면 결과 모름으로 남기고 응답 없음을 그대로 알린다. 결과 모름은 보정이 카드사 기록으로 맞춘다(결정 4).
 */
@Service
@RequiredArgsConstructor
public class PaymentCancelService {

    private final PaymentCancelRecorder paymentCancelRecorder;
    private final PaymentGateway paymentGateway;

    public CancelResult cancel(CancelPaymentCommand command) {
        CancelAttempt attempt = paymentCancelRecorder.request(command);
        if (attempt.isRepeated()) {
            return attempt.repeated();
        }
        CancelOutcome outcome = requestCancel(command, attempt);
        return paymentCancelRecorder.recordOutcome(command.orderId(), command.idempotencyKey(), outcome);
    }

    private CancelOutcome requestCancel(CancelPaymentCommand command, CancelAttempt attempt) {
        try {
            return paymentGateway.cancel(attempt.paymentKey(), command.orderId(), attempt.amount(), command.cancelReason());
        } catch (PaymentGatewayNoResponseException noResponse) {
            paymentCancelRecorder.recordUnknown(command.orderId(), command.idempotencyKey());
            throw noResponse;
        }
    }
}
