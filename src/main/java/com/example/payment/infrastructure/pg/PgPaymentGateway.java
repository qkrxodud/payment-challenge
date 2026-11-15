package com.example.payment.infrastructure.pg;

import com.example.payment.domain.payment.CancelOutcome;
import com.example.payment.domain.payment.GatewayCancel;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.domain.payment.PaymentInquiry;
import com.example.payment.global.error.exception.PaymentGatewayFailedException;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.infrastructure.pg.dto.PgCancel;
import com.example.payment.infrastructure.pg.dto.PgCancelRequest;
import com.example.payment.infrastructure.pg.dto.PgConfirmRequest;
import com.example.payment.infrastructure.pg.dto.PgErrorResponse;
import com.example.payment.infrastructure.pg.dto.PgPaymentResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * PG 승인 API 호출. 2xx 는 승인, 4xx 는 거절로 읽는다.
 * 연결·읽기 중 입출력 실패(시간 초과 포함)는 승인됐는지 알 수 없는 상태라 거절과 구분해 알린다.
 * 4장부터 주문번호로 결제를 조회한다. 결제가 없다는 404(NOT_FOUND_PAYMENT)는 승인 요청이 닿지 않은 것으로 읽는다.
 * 5장부터 취소를 보내고(2xx 취소, 4xx 거절), 조회 응답의 취소 이력(cancels)을 읽는다.
 */
@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

    private static final String PAYMENT_NOT_FOUND = "NOT_FOUND_PAYMENT";

    private final RestClient pgRestClient;

    @Override
    public PaymentApproval approve(String paymentKey, String orderId, BigDecimal amount) {
        try {
            return pgRestClient.post()
                    .uri("/v1/payments/confirm")
                    .body(new PgConfirmRequest(paymentKey, orderId, amount))
                    .exchange((request, response) -> toApproval(orderId, response));
        } catch (ResourceAccessException noResponse) {
            throw new PaymentGatewayNoResponseException(orderId);
        }
    }

    @Override
    public PaymentInquiry inquire(String orderId) {
        try {
            return pgRestClient.get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .exchange((request, response) -> toInquiry(orderId, response));
        } catch (ResourceAccessException noResponse) {
            throw new PaymentGatewayNoResponseException(orderId);
        }
    }

    @Override
    public CancelOutcome cancel(String paymentKey, String orderId, BigDecimal amount, String reason) {
        try {
            return pgRestClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .body(new PgCancelRequest(reason, amount))
                    .exchange((request, response) -> toCancelOutcome(orderId, response));
        } catch (ResourceAccessException noResponse) {
            throw new PaymentGatewayNoResponseException(orderId);
        }
    }

    @Override
    public List<GatewayCancel> findCancels(String orderId) {
        try {
            return pgRestClient.get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .exchange((request, response) -> toCancels(orderId, response));
        } catch (ResourceAccessException noResponse) {
            throw new PaymentGatewayNoResponseException(orderId);
        }
    }

    // 취소 응답은 취소 이력을 담은 결제다. 마지막 취소가 방금 요청한 취소다. 4xx 는 거절로 읽는다
    static CancelOutcome toCancelOutcome(String orderId, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            PgCancel canceled = response.bodyTo(PgPaymentResponse.class).lastCancel();
            return CancelOutcome.canceled(GatewayCancel.of(canceled.transactionKey(), canceled.cancelAmount()));
        }
        if (response.getStatusCode().is4xxClientError()) {
            return CancelOutcome.rejected(response.bodyTo(PgErrorResponse.class).code());
        }
        throw new PaymentGatewayFailedException(orderId, response.getStatusCode().value());
    }

    // 조회 응답의 취소 이력 가운데 카드사가 끝낸(DONE) 취소만 읽는다. 결제가 없다는 답도 오류로 본다 — 취소를 요청한 결제는 승인된 결제다
    private static List<GatewayCancel> toCancels(String orderId, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.bodyTo(PgPaymentResponse.class).doneCancels().stream()
                    .map(canceled -> GatewayCancel.of(canceled.transactionKey(), canceled.cancelAmount()))
                    .toList();
        }
        throw new PaymentGatewayFailedException(orderId, response.getStatusCode().value());
    }

    private static PaymentApproval toApproval(String orderId, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            return PaymentApproval.approved();
        }
        if (response.getStatusCode().is4xxClientError()) {
            return PaymentApproval.declined(response.bodyTo(PgErrorResponse.class).code());
        }
        throw new PaymentGatewayFailedException(orderId, response.getStatusCode().value());
    }

    private static PaymentInquiry toInquiry(String orderId, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            return PgPaymentStatus.inquiryOf(response.bodyTo(PgPaymentResponse.class).status());
        }
        if (isPaymentNotFound(response)) {
            return PaymentInquiry.NOT_APPROVED;
        }
        throw new PaymentGatewayFailedException(orderId, response.getStatusCode().value());
    }

    // 404 라도 결제가 없다는 답일 때만 기록 없음으로 읽는다. 주소를 잘못 부른 404 를 승인 없음으로 읽으면 안 된다
    private static boolean isPaymentNotFound(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        return response.getStatusCode().value() == 404
                && PAYMENT_NOT_FOUND.equals(response.bodyTo(PgErrorResponse.class).code());
    }
}
