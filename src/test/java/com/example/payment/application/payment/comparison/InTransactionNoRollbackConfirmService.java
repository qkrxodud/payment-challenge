package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.error.exception.PaymentNotFoundException;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/** 비교 구현 A': A 와 같지만 거절 예외에는 롤백하지 않고 커밋한다(noRollbackFor) */
@RequiredArgsConstructor
public class InTransactionNoRollbackConfirmService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    @Transactional(noRollbackFor = ApprovalDeclinedException.class)
    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = paymentRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new PaymentNotFoundException(command.orderId()));
        payment.authenticate(command.paymentKey(), clock.instant());

        PaymentApproval approval = paymentGateway.approve(command.paymentKey(), payment.getOrderId(), payment.getAmount());
        if (approval.isDeclined()) {
            payment.failApproval();
            throw new ApprovalDeclinedException(command.orderId());
        }
        payment.approve();
        return PaymentResult.from(payment);
    }
}
