package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentQueryService;
import com.example.payment.application.payment.PaymentRecorder;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import lombok.RequiredArgsConstructor;

/**
 * 비교 구현 C: 시간 초과 직후 요청 안에서 바로 조회. 승인 답을 받지 못하면 결과 모름으로 남기고 곧바로 카드사에 결제를 조회해
 * 그 결과로 닫은 뒤 답한다. 따로 도는 보정은 없다. 조회에도 답이 없으면 응답 없음을 그대로 알린다.
 */
@RequiredArgsConstructor
public class ImmediateLookupConfirmService {

    private final PaymentRecorder paymentRecorder;
    private final PaymentGateway paymentGateway;
    private final PaymentQueryService paymentQueryService;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        Payment payment = paymentRecorder.authenticate(command);
        if (payment.isConfirmedWith(command.paymentKey())) {
            return PaymentResult.from(payment);
        }
        try {
            PaymentApproval approval = paymentGateway.approve(command.paymentKey(), payment.getOrderId(), payment.getAmount());
            return paymentRecorder.recordApproval(command.orderId(), approval);
        } catch (PaymentGatewayNoResponseException noResponse) {
            paymentRecorder.recordApprovalUnknown(command.orderId());
            return lookUpRightAway(command.orderId());
        }
    }

    private PaymentResult lookUpRightAway(String orderId) {
        paymentRecorder.recordInquiry(orderId, paymentGateway.findPayment(orderId));
        return paymentQueryService.find(orderId);
    }
}
