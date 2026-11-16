package com.example.payment.infrastructure.pg;

import com.example.payment.domain.payment.CancelOutcome;
import com.example.payment.domain.payment.GatewayCancel;
import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentInquiry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.client.RestClient;

/**
 * 비교 구현 D(6장 결정 3)의 반쪽: 알림 한 건을 "변화 한 건"으로 처리한다. 중복은 받기 전에 본문 비교로 거른다고 믿기 때문에,
 * 처리할 때 카드사에서 읽은 마지막 취소 하나를 이번 알림이 알린 새 취소로 본다(카드사 transactionKey 대신 처리마다 새 표식을 붙임).
 * 그래서 처리 자체는 멱등이 아니다. 승인·조회·취소 요청은 운영 구현을 그대로 쓴다.
 */
public class LastCancelOnlyPaymentGateway implements PaymentGateway {

    private final PgPaymentGateway delegate;

    public LastCancelOnlyPaymentGateway(RestClient pgRestClient) {
        this.delegate = new PgPaymentGateway(pgRestClient);
    }

    @Override
    public PaymentApproval approve(String paymentKey, String orderId, BigDecimal amount) {
        return delegate.approve(paymentKey, orderId, amount);
    }

    @Override
    public PaymentInquiry inquire(String orderId) {
        return delegate.inquire(orderId);
    }

    @Override
    public CancelOutcome cancel(String paymentKey, String orderId, String idempotencyKey, BigDecimal amount, String reason) {
        return delegate.cancel(paymentKey, orderId, idempotencyKey, amount, reason);
    }

    @Override
    public List<GatewayCancel> findCancels(String orderId) {
        return delegate.findCancels(orderId);
    }

    @Override
    public GatewayPayment findPayment(String orderId) {
        GatewayPayment found = delegate.findPayment(orderId);
        List<GatewayCancel> cancels = found.getCancels();
        if (cancels.isEmpty()) {
            return found;
        }
        GatewayCancel last = cancels.get(cancels.size() - 1);
        GatewayCancel asNew = GatewayCancel.of(last.getTransactionKey() + "#" + UUID.randomUUID(), last.getAmount(),
                last.getReason());
        return GatewayPayment.of(found.getStatus(), List.of(asNew));
    }
}
