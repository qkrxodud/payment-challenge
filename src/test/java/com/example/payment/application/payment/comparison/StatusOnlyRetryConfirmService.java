package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;

/** 비교 구현 D: 결제 키를 보지 않고 상태만으로 다시 온 요청을 가린다. 승인 결과가 남은 결제면 누가 보냈든 그 결과로 답한다 */
@RequiredArgsConstructor
public class StatusOnlyRetryConfirmService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = transactionTemplate.execute(status -> {
            Payment found = find(command.orderId());
            found.verifyAmount(command.amount());
            if (hasResult(found)) {
                return found;
            }
            found.authenticate(command.paymentKey());
            return found;
        });

        if (hasResult(payment)) {
            return PaymentResult.from(payment);
        }

        PaymentApproval approval = paymentGateway.approve(command.paymentKey(), payment.getOrderId(), payment.getAmount());

        return transactionTemplate.execute(status -> {
            Payment found = find(command.orderId());
            if (approval.isDeclined()) {
                found.failApproval();
                return PaymentResult.from(found);
            }
            found.approve();
            return PaymentResult.from(found);
        });
    }

    private Payment find(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    private static boolean hasResult(Payment payment) {
        return payment.getStatus() == PaymentStatus.APPROVED || payment.getStatus() == PaymentStatus.APPROVAL_FAILED;
    }
}
