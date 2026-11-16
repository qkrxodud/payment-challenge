package com.example.payment.infrastructure.pg;

import com.example.payment.domain.payment.CancelOutcome;
import com.example.payment.domain.payment.GatewayCancel;
import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentInquiry;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.infrastructure.pg.dto.PgCancelRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 비교 구현 D(5장 결정 5): 카드사에 멱등키를 넘기지 않음. 취소 요청에 Idempotency-Key 헤더를 붙이지 않는 것만 운영 구현과 다르다.
 * 승인·조회는 운영 구현을 그대로 쓴다.
 */
public class NoIdempotencyKeyPaymentGateway implements PaymentGateway {

    private final PgPaymentGateway delegate;
    private final RestClient pgRestClient;

    public NoIdempotencyKeyPaymentGateway(RestClient pgRestClient) {
        this.delegate = new PgPaymentGateway(pgRestClient);
        this.pgRestClient = pgRestClient;
    }

    @Override
    public PaymentApproval approve(String paymentKey, String orderId, BigDecimal amount) {
        return delegate.approve(paymentKey, orderId, amount);
    }

    @Override
    public PaymentInquiry inquire(String orderId) {
        return delegate.inquire(orderId);
    }

    // 받은 멱등키를 버리고 보낸다
    @Override
    public CancelOutcome cancel(String paymentKey, String orderId, String idempotencyKey, BigDecimal amount, String reason) {
        try {
            return pgRestClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .body(new PgCancelRequest(reason, amount))
                    .exchange((request, response) -> PgPaymentGateway.toCancelOutcome(orderId, response));
        } catch (ResourceAccessException noResponse) {
            throw new PaymentGatewayNoResponseException(orderId);
        }
    }

    @Override
    public List<GatewayCancel> findCancels(String orderId) {
        return delegate.findCancels(orderId);
    }

    @Override
    public GatewayPayment findPayment(String orderId) {
        return delegate.findPayment(orderId);
    }
}
