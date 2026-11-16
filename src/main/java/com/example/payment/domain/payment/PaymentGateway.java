package com.example.payment.domain.payment;

import java.math.BigDecimal;
import java.util.List;

/**
 * 결제를 승인해 주는 바깥 PG. 승인과 거절은 결과로 돌려주고,
 * 정해진 시간 안에 답을 받지 못하면 PaymentGatewayNoResponseException 을 던진다.
 */
public interface PaymentGateway {

    PaymentApproval approve(String paymentKey, String orderId, BigDecimal amount);

    // 4장부터: 주문번호로 카드사에 남은 결제 결과를 묻는다. 시간 안에 답이 없으면 승인과 같은 예외를 던진다
    PaymentInquiry inquire(String orderId);

    // 5장부터: 승인된 결제를 취소한다. 취소와 거절은 결과로 돌려주고, 시간 안에 답이 없으면 승인과 같은 예외를 던진다.
    // 고객이 보낸 Idempotency-Key 를 카드사에도 넘긴다. 같은 키로 다시 보내면 카드사는 다시 취소하지 않고 처음 답을 준다
    CancelOutcome cancel(String paymentKey, String orderId, String idempotencyKey, BigDecimal amount, String reason);

    // 5장부터: 주문번호로 카드사에 남은 취소들을 묻는다
    List<GatewayCancel> findCancels(String orderId);

    // 6장부터: 주문번호로 카드사에 남은 결제 상태와 취소들을 함께 묻는다(웹훅을 받으면 본문 대신 이것을 믿는다).
    // 카드사에 결제가 없으면 없다는 답(GatewayPayment.notFound)을 준다. 시간 안에 답이 없으면 승인과 같은 예외를 던진다
    GatewayPayment findPayment(String orderId);
}
