package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 승인. 트랜잭션을 둘로 나누고 PG 호출은 그 사이, 트랜잭션 밖에서 한다.
 * PG 답을 기다리는 동안 DB 연결을 붙잡지 않기 위해서다.
 * 거절도 결과로 기록하고 커밋한다 — 예외로 알리면 거절 기록까지 롤백된다.
 * PG 에는 요청 금액이 아니라 준비할 때 저장한 금액을 보낸다. 둘이 다르면 인증 단계에서 이미 거부된다.
 * 같은 결제 키의 요청이 다시 오면 PG 를 다시 부르지 않고 기록해 둔 결과로 답한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final PaymentRecorder paymentRecorder;
    private final PaymentGateway paymentGateway;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = paymentRecorder.authenticate(command);
        if (payment.isConfirmedWith(command.paymentKey())) {
            return PaymentResult.from(payment);
        }
        PaymentApproval approval = paymentGateway.approve(command.paymentKey(), payment.getOrderId(), payment.getAmount());
        return paymentRecorder.recordApproval(command.orderId(), approval);
    }
}
