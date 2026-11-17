package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.PaymentAction;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.InvalidPaymentTransitionException;
import lombok.RequiredArgsConstructor;

/**
 * 비교 구현(7장 결정 2): 지금처럼(6장까지) 할 수 없다고 답함. 카드사 답을 기다리는(인증 상태) 결제에 같은 결제 키로 다시 온 승인을
 * 인증할 수 없는 상태로 거부한다(409). 카드사에 다시 보내지 않는 것은 운영 구현과 같다
 */
@RequiredArgsConstructor
public class RejectInProgressConfirmService {

    private final PaymentConfirmService paymentConfirmService;

    public PaymentResult confirm(ConfirmPaymentCommand command) {
        PaymentResult result = paymentConfirmService.confirm(command);
        if (result.status() == PaymentStatus.AUTHENTICATED) {
            throw new InvalidPaymentTransitionException(result.status().name(), PaymentAction.AUTHENTICATE.name());
        }
        return result;
    }
}
