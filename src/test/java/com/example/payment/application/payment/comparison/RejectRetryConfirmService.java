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
import org.springframework.transaction.support.TransactionTemplate;

/** 비교 구현 C: 금액은 확인하지만 다시 온 요청을 가리지 않는다. 승인된 결제에 다시 오면 상태 전이 거부(409)로 끝난다 */
@RequiredArgsConstructor
public class RejectRetryConfirmService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = transactionTemplate.execute(status -> {
            Payment found = find(command.orderId());
            found.verifyAmount(command.amount());
            found.authenticate(command.paymentKey(), clock.instant());
            return found;
        });

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
}
