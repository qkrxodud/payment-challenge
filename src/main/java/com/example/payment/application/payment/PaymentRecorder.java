package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.ConfirmAttempt;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인 앞뒤의 기록을 각각 짧은 트랜잭션으로 남긴다.
 * PaymentConfirmService 와 다른 빈으로 둔 이유: 같은 클래스 안에서 부르면 @Transactional 이 적용되지 않는다.
 * 7장부터 결제 줄을 잠그고 읽는다(결정 1). 같은 결제의 승인·알림 반영·보정 기록이 겹치면 나중 기록은 앞 기록이 끝날 때까지 기다렸다가
 * 앞 기록이 남긴 결제를 보고 판단한다. 카드사 호출은 이미 트랜잭션 밖이라(2장) 잠금은 기록하는 동안만 쥔다.
 */
@Component
@RequiredArgsConstructor
public class PaymentRecorder {

    private final PaymentRepository paymentRepository;
    private final PaymentHoldings paymentHoldings;
    private final Clock clock;

    // 금액이 다르면 상태를 바꾸지 않고 거부한다. 같은 요청이 다시 왔으면 아무것도 바꾸지 않고 지금 결과를 돌려준다.
    // 7장부터(결정 2) 카드사 답을 기다리는(인증 상태) 결제에 같은 결제 키로 온 요청도 다시 온 요청으로 본다 — 카드사에 다시 보내지 않고
    // 지금 상태(인증)로 답한다(API 는 4장 결과 모름처럼 확인 중 202). 다른 결제 키는 지금처럼 인증할 수 없는 상태로 거부한다(409).
    // 8장 결정 2부터 인증과 같은 트랜잭션에서 담긴 상품의 재고와 쓴 쿠폰·포인트를 잡는다. 모자라면 인증도 남지 않고(준비 상태 그대로)
    // 카드사에 승인을 요청하지 않는다
    @Transactional
    public ConfirmAttempt authenticate(ConfirmPaymentCommand command) {
        Payment payment = find(command.orderId());
        payment.verifyAmount(command.amount());
        if (payment.isRequestedWith(command.paymentKey())) {
            return ConfirmAttempt.repeat(PaymentResult.from(payment));
        }
        payment.authenticate(command.paymentKey(), clock.instant());
        paymentHoldings.hold(payment);
        return ConfirmAttempt.send(payment.getOrderId(), payment.getAmount());
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

    // 카드사에 확인한 결과를 남긴다. 그 사이 다른 길로 결과가 남은 결제는 건드리지 않는다.
    // 6장부터 카드사 결제 상태와 취소 이력을 함께 받아 웹훅과 같은 길로 닫는다(카드사에서 승인 뒤 취소됐으면 승인 취소)
    @Transactional
    public void recordInquiry(String orderId, GatewayPayment gatewayPayment) {
        Payment payment = find(orderId);
        if (payment.isAwaitingApprovalResult()) {
            payment.reconcile(gatewayPayment, clock.instant());
        }
    }

    private Payment find(String orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }
}
