package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 결제 준비. 같은 주문번호가 같은 금액으로 다시 오면 이미 있는 결제를 지금 상태 그대로 돌려주고, 금액이 다르면 거부한다(3장).
 * 7장부터(결정 3) 같은 주문의 준비가 동시에 와서 둘 다 결제를 찾지 못하고 남기려 하면, 나중 준비는 주문번호가 두 번 올라갈 수 없다는
 * 거부(uk_payment_order_id)를 받는다. 그 거부를 잡아 새 트랜잭션에서 먼저 남은 결제를 다시 읽고 3장 규칙대로 답한다 —
 * 금액이 같으면 그 결제(201), 다르면 이미 있는 주문(409). 다시 읽어도 결제가 없으면 주문번호 중복이 아닌 거부라 그대로 알린다.
 */
@Service
@RequiredArgsConstructor
public class PaymentPrepareService {

    private final PaymentPrepareRecorder paymentPrepareRecorder;

    public PaymentResult prepare(PreparePaymentCommand command) {
        try {
            return paymentPrepareRecorder.prepare(command);
        } catch (DataIntegrityViolationException duplicated) {
            return paymentPrepareRecorder.prepareAgain(command)
                    .orElseThrow(() -> duplicated);
        }
    }
}
