package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentWebhookRecorder;
import com.example.payment.application.payment.dto.ReceiveWebhookCommand;
import com.example.payment.domain.error.exception.InvalidPaymentTransitionException;
import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.GatewayStatus;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentAction;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 비교 구현 B(6장 결정 2): 허용 전이 표에 없으면 거부한다. 카드사 조회로 다시 읽는 것은 운영 구현과 같지만,
 * 카드사 상태를 행위 하나로 바꿔(승인 → 승인, 거절·만료 → 승인 실패, 취소·부분 취소 → 취소) 지금 상태에서 그 행위가 표에 없으면
 * 지금 상태에서 할 수 없다(409)고 답한다. 오류로 답하면 카드사가 같은 알림을 다시 보낸다. 받은 자리에서 조회하고 옮긴다.
 */
@Service
@RequiredArgsConstructor
public class RejectOffTableWebhookService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentWebhookRecorder paymentWebhookRecorder;

    public void receive(ReceiveWebhookCommand command) {
        Payment payment = paymentRepository.findByOrderId(command.orderId()).orElse(null);
        if (isNothingToFollow(command, payment)) {
            return;
        }
        GatewayPayment gatewayPayment = paymentGateway.findPayment(command.orderId());
        PaymentAction action = actionOf(gatewayPayment.getStatus());
        if (Objects.nonNull(action) && payment.getStatus().refuses(action)) {
            throw new InvalidPaymentTransitionException(payment.getStatus().name(), action.name());
        }
        paymentWebhookRecorder.follow(command.orderId(), gatewayPayment);
    }

    private static boolean isNothingToFollow(ReceiveWebhookCommand command, Payment payment) {
        if (command.isPaymentStatusChange()) {
            return Objects.isNull(payment);
        }
        return true;
    }

    private static PaymentAction actionOf(GatewayStatus status) {
        return switch (status) {
            case DONE -> PaymentAction.APPROVE;
            case ABORTED, EXPIRED -> PaymentAction.FAIL_APPROVAL;
            case CANCELED, PARTIAL_CANCELED -> PaymentAction.CANCEL;
            default -> null;
        };
    }
}
