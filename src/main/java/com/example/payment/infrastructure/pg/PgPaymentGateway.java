package com.example.payment.infrastructure.pg;

import com.example.payment.domain.payment.PaymentApproval;
import com.example.payment.domain.payment.PaymentGateway;
import com.example.payment.global.error.exception.PaymentGatewayFailedException;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.infrastructure.pg.dto.PgConfirmRequest;
import com.example.payment.infrastructure.pg.dto.PgErrorResponse;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

/**
 * PG 승인 API 호출. 2xx 는 승인, 4xx 는 거절로 읽는다.
 * 연결·읽기 중 입출력 실패(시간 초과 포함)는 승인됐는지 알 수 없는 상태라 거절과 구분해 알린다.
 */
@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

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

    private static PaymentApproval toApproval(String orderId, ConvertibleClientHttpResponse response)
            throws java.io.IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            return PaymentApproval.approved();
        }
        if (response.getStatusCode().is4xxClientError()) {
            return PaymentApproval.declined(response.bodyTo(PgErrorResponse.class).code());
        }
        throw new PaymentGatewayFailedException(orderId, response.getStatusCode().value());
    }
}
