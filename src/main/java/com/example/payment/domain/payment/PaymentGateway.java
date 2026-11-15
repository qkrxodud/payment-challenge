package com.example.payment.domain.payment;

import java.math.BigDecimal;

/**
 * 결제를 승인해 주는 바깥 PG. 승인과 거절은 결과로 돌려주고,
 * 정해진 시간 안에 답을 받지 못하면 PaymentGatewayNoResponseException 을 던진다.
 */
public interface PaymentGateway {

    PaymentApproval approve(String paymentKey, String orderId, BigDecimal amount);

    // 4장부터: 주문번호로 카드사에 남은 결제 결과를 묻는다. 시간 안에 답이 없으면 승인과 같은 예외를 던진다
    PaymentInquiry inquire(String orderId);

    // 5장부터: 승인된 결제를 취소하고, 카드사에 남은 취소 한 건을 돌려준다
    GatewayCancel cancel(String paymentKey, String orderId, BigDecimal amount, String reason);
}
