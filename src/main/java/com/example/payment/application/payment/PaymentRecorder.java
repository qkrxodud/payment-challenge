package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentInquiry;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인 앞뒤의 기록을 각각 짧은 트랜잭션으로 남긴다.
 * PaymentConfirmService 와 다른 빈으로 둔 이유: 같은 클래스 안에서 부르면 @Transactional 이 적용되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentRecorder {

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    // 금액이 다르면 상태를 바꾸지 않고 거부한다. 같은 요청이 다시 왔으면 아무것도 바꾸지 않고 돌려준다
    @Transactional
    public Payment authenticate(ConfirmPaymentCommand command) {
        Payment payment = find(command.orderId());
        payment.verifyAmount(command.amount());
        if (payment.isConfirmedWith(command.paymentKey())) {
            return payment;
        }
        payment.authenticate(command.paymentKey(), clock.instant());
        return payment;
    }

    // 6장부터: 승인 답을 기다리는 사이 웹훅이 먼저 결과를 남겼으면 건드리지 않고 지금 결과로 답한다
    @Transactional
    public PaymentResult recordApproval(String orderId, PaymentApproval approval) {
        Payment payment = find(orderId);
        if (payment.isAwaitingApprovalResult()) {
            settleWith(payment, approval);
        }
        return PaymentResult.from(payment);
    }

    private static void settleWith(Payment payment, PaymentApproval approval) {
        if (approval.isDeclined()) {
            payment.failApproval();
            return;
        }
        payment.approve();
    }

    // 카드사 답을 받지 못했다. 승인됐는지 모른다는 것을 남겨 두고 나중에 카드사에 확인한다.
    // 6장부터: 그 사이 웹훅이 먼저 결과를 남겼으면 결과 모름으로 되돌리지 않는다
    @Transactional
    public void recordApprovalUnknown(String orderId) {
        Payment payment = find(orderId);
        if (payment.isAwaitingApprovalResult()) {
            payment.markApprovalUnknown();
        }
    }

    // 카드사에 확인한 결과를 남긴다. 그 사이 다른 길로 결과가 남은 결제는 건드리지 않는다
    @Transactional
    public void recordInquiry(String orderId, PaymentInquiry inquiry) {
        Payment payment = find(orderId);
        if (payment.isAwaitingApprovalResult()) {
            payment.settle(inquiry);
        }
    }

    private Payment find(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }
}
