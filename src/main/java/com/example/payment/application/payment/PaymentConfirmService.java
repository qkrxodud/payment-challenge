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
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final PaymentRecorder paymentRecorder;
    private final PaymentGateway paymentGateway;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = paymentRecorder.authenticate(command.orderId());
        PaymentApproval approval = paymentGateway.approve(command.paymentKey(), payment.getOrderId(), payment.getAmount());
        return paymentRecorder.recordApproval(command.orderId(), approval);
    }
}
