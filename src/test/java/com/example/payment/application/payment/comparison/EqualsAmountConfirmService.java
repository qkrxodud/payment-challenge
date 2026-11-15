package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.AmountMismatchException;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;

/** 비교 구현 B: 금액을 BigDecimal.equals 로 비교한다. equals 는 소수 자릿수까지 같아야 같다고 본다 */
@RequiredArgsConstructor
public class EqualsAmountConfirmService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = transactionTemplate.execute(status -> {
            Payment found = find(command.orderId());
            if (differs(found.getAmount(), command.amount())) {
                throw new AmountMismatchException(command.orderId());
            }
            if (found.isConfirmedWith(command.paymentKey())) {
                return found;
            }
            found.authenticate(command.paymentKey());
            return found;
        });

        if (payment.isConfirmedWith(command.paymentKey())) {
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

    private static boolean differs(BigDecimal prepared, BigDecimal requested) {
        return !prepared.equals(requested);
    }
}
