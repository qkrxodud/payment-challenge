package com.example.payment.infrastructure.pg;

import com.example.payment.domain.payment.CancelOutcome;
import com.example.payment.domain.payment.GatewayCancel;
import com.example.payment.domain.payment.GatewayPayment;
import com.example.payment.domain.payment.GatewayStatus;
import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
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
 * 4장부터 주문번호로 결제를 조회한다. 결제가 없다는 404(NOT_FOUND_PAYMENT)는 카드사에 기록이 없다는 답으로 읽는다.
 * 5장부터 취소를 보내고(2xx 취소, 4xx 거절), 조회 응답의 취소 이력(cancels)을 읽는다. 취소에는 멱등키 헤더를 붙인다.
 * 6장부터 조회 응답의 상태와 취소 이력을 함께 읽는다(웹훅 처리, 4장 보정도 같이 쓴다).
 */
@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

    private static final String PAYMENT_NOT_FOUND = "NOT_FOUND_PAYMENT";
    // 5장: 토스페이먼츠 멱등키 헤더와, 같은 키의 앞 요청이 처리 중일 때의 오류 코드
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENT_REQUEST_PROCESSING = "IDEMPOTENT_REQUEST_PROCESSING";

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
    public CancelOutcome cancel(String paymentKey, String orderId, String idempotencyKey, BigDecimal amount, String reason) {
        try {
            return pgRestClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .header(IDEMPOTENCY_KEY, idempotencyKey)
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

    @Override
    public GatewayPayment findPayment(String orderId) {
        try {
            return pgRestClient.get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .exchange((request, response) -> toGatewayPayment(orderId, response));
        } catch (ResourceAccessException noResponse) {
            throw new PaymentGatewayNoResponseException(orderId);
        }
    }

    // 취소 응답은 취소 이력을 담은 결제다. 마지막 취소가 방금 요청한 취소다.
    // 4xx 는 거절로 읽되, 같은 멱등키의 앞 요청을 아직 처리 중이라는 409(IDEMPOTENT_REQUEST_PROCESSING)는 답이 아직 없는 것으로 읽는다
    static CancelOutcome toCancelOutcome(String orderId, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            PgCancel canceled = response.bodyTo(PgPaymentResponse.class).lastCancel();
            return CancelOutcome.canceled(GatewayCancel.of(canceled.transactionKey(), canceled.cancelAmount()));
        }
        if (response.getStatusCode().is4xxClientError()) {
            return rejectedOrInProgress(response.bodyTo(PgErrorResponse.class).code());
        }
        throw new PaymentGatewayFailedException(orderId, response.getStatusCode().value());
    }

    private static CancelOutcome rejectedOrInProgress(String code) {
        if (IDEMPOTENT_REQUEST_PROCESSING.equals(code)) {
            return CancelOutcome.inProgress();
        }
        return CancelOutcome.rejected(code);
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

    // 결제가 없다는 404(NOT_FOUND_PAYMENT)는 카드사에 따를 기록이 없다는 답으로 읽는다
    private static GatewayPayment toGatewayPayment(String orderId,
                                                   RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            return gatewayPaymentOf(response.bodyTo(PgPaymentResponse.class));
        }
        if (isPaymentNotFound(response)) {
            return GatewayPayment.notFound();
        }
        throw new PaymentGatewayFailedException(orderId, response.getStatusCode().value());
    }

    // 카드사 Payment 객체를 우리가 읽는 결제로 옮긴다. 취소는 카드사가 끝낸(DONE) 것만 읽는다
    static GatewayPayment gatewayPaymentOf(PgPaymentResponse payment) {
        List<GatewayCancel> cancels = payment.doneCancels().stream()
                .map(canceled -> GatewayCancel.of(canceled.transactionKey(), canceled.cancelAmount(), canceled.cancelReason()))
                .toList();
        return GatewayPayment.of(GatewayStatus.of(payment.status()), cancels);
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

    // 404 라도 결제가 없다는 답일 때만 기록 없음으로 읽는다. 주소를 잘못 부른 404 를 기록 없음(보정은 승인 실패로 닫음)으로 읽으면 안 된다
    private static boolean isPaymentNotFound(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        return response.getStatusCode().value() == 404
                && PAYMENT_NOT_FOUND.equals(response.bodyTo(PgErrorResponse.class).code());
    }
}
