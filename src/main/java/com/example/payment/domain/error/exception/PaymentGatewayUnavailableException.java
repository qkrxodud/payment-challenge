package com.example.payment.domain.error.exception;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;

/** 12장 결정 4: 카드사 장애로 차단기가 열려 승인을 요청하지 않았다. 카드사에 보내지 않았으니 승인됐을 수 없다 */
public class PaymentGatewayUnavailableException extends PaymentChallengeException {

    public PaymentGatewayUnavailableException(String orderId) {
        super(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, "The gateway is unavailable. No approval was requested for order " + orderId + ".");
    }
}
