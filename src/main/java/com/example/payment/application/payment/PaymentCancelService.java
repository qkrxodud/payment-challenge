package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.domain.payment.GatewayCancel;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentGateway;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 취소(5장). 카드사가 취소하면 그 취소를 한 건으로 남긴다(결정 1).
 * 카드사에 보내기 전에 승인이 남은 결제인지, 남은 금액 안의 취소인지 확인한다. 금액이 없으면 남은 금액 전부를 취소한다(결정 2).
 * 2장처럼 카드사 호출은 트랜잭션 밖에서 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentCancelService {

    private final PaymentCancelRecorder paymentCancelRecorder;
    private final PaymentGateway paymentGateway;

    public CancelResult cancel(CancelPaymentCommand command) {
        Payment payment = paymentCancelRecorder.find(command.orderId());
        BigDecimal amount = paymentCancelRecorder.checkCancel(command);
        GatewayCancel canceled = paymentGateway.cancel(payment.getPaymentKey(), payment.getOrderId(), amount, command.cancelReason());
        return paymentCancelRecorder.recordCanceled(command, amount, canceled);
    }
}
