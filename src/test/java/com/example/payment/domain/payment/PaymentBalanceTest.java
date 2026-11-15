package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("결제 취소와 남은 금액")
class PaymentBalanceTest {

    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-11-16T01:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-16T02:00:00Z");
    private static final String REASON = "Customer changed their mind";

    @Test
    @DisplayName("카드사가 취소한 금액만큼 결제의 남은 금액이 줄어든다")
    void canceledAmountIsTakenFromBalance() {
        // given: 15000원 승인된 결제
        Payment payment = approvedPayment();

        // when
        payment.requestCancel(request("cancel-1"), REQUESTED_AT);
        payment.completeCancel("cancel-1", GatewayCancel.of("tx-1", new BigDecimal("5000")));

        // then
        assertThat(payment.balanceAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("남긴 취소는 고객이 붙여 보낸 Idempotency-Key 로 다시 찾을 수 있다")
    void cancelIsFoundByIdempotencyKey() {
        // given: 15000원 승인된 결제
        Payment payment = approvedPayment();

        // when
        payment.requestCancel(request("cancel-1"), REQUESTED_AT);
        payment.completeCancel("cancel-1", GatewayCancel.of("tx-1", new BigDecimal("5000")));

        // then
        assertThat(payment.findCancel("cancel-1")).get()
                .extracting(PaymentCancel::getTransactionKey)
                .isEqualTo("tx-1");
    }

    @Test
    @DisplayName("취소가 없는 결제의 남은 금액은 결제 금액 그대로다")
    void balanceWithoutCancelIsAmount() {
        Payment payment = approvedPayment();

        assertThat(payment.balanceAmount()).isEqualByComparingTo("15000");
    }

    private static CancelRequest request(String idempotencyKey) {
        return CancelRequest.of("order-20261116-0001", idempotencyKey, new BigDecimal("5000"), REASON);
    }

    private static Payment approvedPayment() {
        Payment payment = Payment.create("order-20261116-0001", new BigDecimal("15000"));
        payment.authenticate("key-1", AUTHENTICATED_AT);
        payment.approve();
        return payment;
    }
}
