package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("카드사 답을 기다리는 취소를 카드사 취소 이력으로 바로 닫기")
class AwaitingCancelFollowTest {

    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-11-16T01:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-16T02:00:00Z");
    // 보정 대기 시간(1분)이 지나기 전
    private static final Instant NOW = REQUESTED_AT.plusSeconds(2);
    private static final String ORDER_ID = "order-20261116-0001";
    private static final String KEY = "cancel-1";
    private static final BigDecimal PREPARED = new BigDecimal("15000");
    private static final BigDecimal PART = new BigDecimal("5000");

    @Test
    @DisplayName("결과 모름 취소는 같은 금액의 카드사 취소를 읽으면 보정 대기 시간 전이라도 끝난 취소로 닫힌다")
    void unknownCancelIsCompletedBySameAmount() {
        Payment payment = paymentWithUnknownCancel();

        payment.follow(partiallyCanceled(GatewayCancel.of("tx-1", PART, "Customer changed their mind")), NOW);

        assertThat(payment.findCancel(KEY).orElseThrow().getStatus()).isEqualTo(CancelStatus.DONE);
    }

    @Test
    @DisplayName("결과 모름 취소와 짝지은 카드사 취소는 따로 한 번 더 들여오지 않는다")
    void matchedGatewayCancelIsNotRecordedAgain() {
        Payment payment = paymentWithUnknownCancel();

        payment.follow(partiallyCanceled(GatewayCancel.of("tx-1", PART, "Customer changed their mind")), NOW);

        assertThat(payment.getCancels()).hasSize(1);
    }

    @Test
    @DisplayName("카드사 답 전에 요청 상태로 남은 취소도 같은 금액의 카드사 취소로 닫힌다")
    void requestedCancelIsCompletedBySameAmount() {
        Payment payment = approvedPayment();
        payment.requestCancel(CancelRequest.of(ORDER_ID, KEY, PART, "Customer changed their mind"), REQUESTED_AT);

        payment.follow(partiallyCanceled(GatewayCancel.of("tx-1", PART, "Customer changed their mind")), NOW);

        assertThat(payment.findCancel(KEY).orElseThrow().getStatus()).isEqualTo(CancelStatus.DONE);
    }

    @Test
    @DisplayName("카드사에 같은 금액의 취소가 없으면 결과 모름 취소를 실패로 닫지 않고 그대로 둔다")
    void unmatchedUnknownCancelStaysUnknown() {
        Payment payment = paymentWithUnknownCancel();

        payment.follow(partiallyCanceled(GatewayCancel.of("tx-1", new BigDecimal("3000"), "Console")), NOW);

        assertThat(payment.findCancel(KEY).orElseThrow().getStatus()).isEqualTo(CancelStatus.UNKNOWN);
    }

    @Test
    @DisplayName("결과 모름 취소와 짝이 맞지 않은 카드사 취소는 카드사에서만 일어난 취소로 들여온다")
    void unmatchedGatewayCancelIsRecordedSeparately() {
        Payment payment = paymentWithUnknownCancel();

        payment.follow(partiallyCanceled(GatewayCancel.of("tx-1", new BigDecimal("3000"), "Console")), NOW);

        assertThat(payment.balanceAmount()).isEqualByComparingTo("12000");
    }

    private static GatewayPayment partiallyCanceled(GatewayCancel... cancels) {
        return GatewayPayment.of(GatewayStatus.PARTIAL_CANCELED, List.of(cancels));
    }

    private static Payment paymentWithUnknownCancel() {
        Payment payment = approvedPayment();
        payment.requestCancel(CancelRequest.of(ORDER_ID, KEY, PART, "Customer changed their mind"), REQUESTED_AT);
        payment.markCancelUnknown(KEY);
        return payment;
    }

    private static Payment approvedPayment() {
        Payment payment = Payment.create(ORDER_ID, PREPARED);
        payment.authenticate("key-" + ORDER_ID, AUTHENTICATED_AT);
        payment.approve();
        return payment;
    }
}
