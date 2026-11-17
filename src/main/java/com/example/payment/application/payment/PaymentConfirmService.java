package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ConfirmAttempt;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 승인. 트랜잭션을 둘로 나누고 PG 호출은 그 사이, 트랜잭션 밖에서 한다.
 * PG 답을 기다리는 동안 DB 연결을 붙잡지 않기 위해서다.
 * 거절도 결과로 기록하고 커밋한다 — 예외로 알리면 거절 기록까지 롤백된다.
 * PG 에는 요청 금액이 아니라 준비할 때 저장한 금액을 보낸다. 둘이 다르면 인증 단계에서 이미 거부된다.
 * 같은 결제 키의 요청이 다시 오면 PG 를 다시 부르지 않고 기록해 둔 결과로 답한다.
 * PG 답을 정해진 시간 안에 받지 못하면 결과 모름으로 기록하고 응답 없음을 그대로 알린다(4장).
 * 7장부터 카드사 답을 기다리는 결제에 같은 결제 키로 다시 온 요청도 PG 를 부르지 않고 지금 상태(인증)로 답한다(결정 2).
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final PaymentRecorder paymentRecorder;
    private final PaymentGateway paymentGateway;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        ConfirmAttempt attempt = paymentRecorder.authenticate(command);
        if (attempt.isRepeated()) {
            return attempt.repeated();
        }
        PaymentApproval approval = requestApproval(command.paymentKey(), attempt);
        return paymentRecorder.recordApproval(command.orderId(), approval);
    }

    // 답을 받지 못했어도 카드사에는 승인이 남았을 수 있다. 인증 상태로 두지 않고 결과 모름으로 남긴다
    private PaymentApproval requestApproval(String paymentKey, ConfirmAttempt attempt) {
        try {
            return paymentGateway.approve(paymentKey, attempt.orderId(), attempt.amount());
        } catch (PaymentGatewayNoResponseException noResponse) {
            paymentRecorder.recordApprovalUnknown(attempt.orderId());
            throw noResponse;
        }
    }
}
