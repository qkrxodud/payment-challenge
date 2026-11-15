package com.example.payment.application.payment.comparison;

import com.example.payment.application.payment.PaymentCancelService;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

/**
 * 비교 구현 A(5장 결정 3): 자연 키. Idempotency-Key 를 보지 않고, 같은 결제에 같은 금액·사유로 온 취소를 같은 요청으로 본다.
 * 금액·사유로 만든 값을 키 자리에 넣어 운영 구현을 그대로 부른다. 기록·카드사 호출 순서·보정은 운영 구현과 같다.
 */
@RequiredArgsConstructor
public class NaturalKeyCancelService {

    private final PaymentCancelService paymentCancelService;

    public CancelResult cancel(CancelPaymentCommand command) {
        String naturalKey = "natural-" + Objects.toString(command.cancelAmount(), "all") + "-" + command.cancelReason();
        return paymentCancelService.cancel(new CancelPaymentCommand(
                command.orderId(), naturalKey, command.cancelAmount(), command.cancelReason()));
    }
}
