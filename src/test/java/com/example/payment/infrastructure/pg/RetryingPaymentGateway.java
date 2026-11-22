package com.example.payment.infrastructure.pg;

import com.example.payment.domain.error.exception.PaymentGatewayFailedException;
import com.example.payment.domain.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.domain.payment.PaymentApproval;
import java.math.BigDecimal;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * 12장 결정 4 비교: 다시 시도. 차단기 없이, 답이 없거나 카드사 오류인 승인을 바로 두 번 더 보낸다(모두 세 번). 세 번 모두 실패하면 마지막 실패를 그대로 알린다 —
 * 답이 없었으면 결과 모름. 카드사 승인 API 에는 멱등키를 붙이지 않으므로(3장부터 같은 결제 키를 서버가 가린다) 답만 늦은 승인을 다시 보내면 카드사에 승인이 두 번 갈 수 있다
 */
@Primary
public class RetryingPaymentGateway extends PgPaymentGateway {

    static final int ATTEMPTS = 3;

    public RetryingPaymentGateway(RestClient pgRestClient) {
        super(pgRestClient, DisabledCircuit.create());
    }

    @Override
    public PaymentApproval approve(String paymentKey, String orderId, BigDecimal amount) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                return super.approve(paymentKey, orderId, amount);
            } catch (PaymentGatewayNoResponseException | PaymentGatewayFailedException failed) {
                last = failed;
            }
        }
        throw last;
    }
}
