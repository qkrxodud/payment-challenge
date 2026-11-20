package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payment.domain.error.exception.CancelAmountExceedsBalanceException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("카드사 답을 기다리는 취소와 카드사에 확인한 결과로 취소 닫기")
class CancelSettleTest {

    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-11-16T01:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-16T02:00:00Z");
    // 보정 대기 시간이 지나 REQUESTED_AT 에 요청한 취소를 보정하는 때
    private static final Instant SETTLE_BEFORE = REQUESTED_AT.plusSeconds(60);
    private static final String ORDER_ID = "order-20261116-0001";
    private static final String REASON = "Customer changed their mind";
    private static final BigDecimal PART = new BigDecimal("5000");

    @Test
    @DisplayName("카드사 답을 기다리는 취소 금액은 새 취소로 받을 수 있는 금액에서 뺀다")
    void awaitingCancelIsReservedFromNewCancel() {
        // given: 15000원 결제에 10000원 취소가 카드사 답을 기다린다
        Payment payment = approvedPayment();
        payment.requestCancel(request("cancel-1", new BigDecimal("10000")), REQUESTED_AT);

        // when & then
        assertThatThrownBy(() -> payment.requestCancel(request("cancel-2", new BigDecimal("5001")), REQUESTED_AT))
                .isInstanceOf(CancelAmountExceedsBalanceException.class);
    }

    @Test
    @DisplayName("카드사 답을 기다리는 취소는 아직 남은 금액을 줄이지 않는다")
    void awaitingCancelDoesNotReduceBalance() {
        Payment payment = approvedPayment();

        payment.requestCancel(request("cancel-1", PART), REQUESTED_AT);

        assertThat(payment.balanceAmount()).isEqualByComparingTo("15000");
    }

    @Test
    @DisplayName("카드사가 거절한 취소는 남은 금액을 줄이지 않는다")
    void rejectedCancelDoesNotReduceBalance() {
        Payment payment = approvedPayment();
        payment.requestCancel(request("cancel-1", PART), REQUESTED_AT);

        payment.failCancel("cancel-1");

        assertThat(payment.balanceAmount()).isEqualByComparingTo("15000");
    }

    @Test
    @DisplayName("결과를 모르는 취소와 같은 금액의 취소가 카드사에 있으면 그 취소로 끝난 것으로 닫는다")
    void unknownCancelIsCompletedByCardCompanyCancel() {
        // given: 카드사 답을 받지 못한 5000원 취소
        Payment payment = unknownCancel("cancel-1", PART);

        // when
        payment.settleCancels(List.of(GatewayCancel.of("tx-1", PART)), SETTLE_BEFORE);

        // then
        assertThat(payment.findCancel("cancel-1").orElseThrow().getStatus()).isEqualTo(CancelStatus.DONE);
    }

    @Test
    @DisplayName("결과를 모르는 취소를 카드사 취소로 닫으면 남은 금액도 그만큼 줄어든다")
    void completedBySettleReducesBalance() {
        Payment payment = unknownCancel("cancel-1", PART);

        payment.settleCancels(List.of(GatewayCancel.of("tx-1", PART)), SETTLE_BEFORE);

        assertThat(payment.findCancel("cancel-1").orElseThrow().getBalanceAfter()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("결과를 모르는 취소가 카드사에 없으면 카드사가 취소하지 않은 것으로 닫는다")
    void unknownCancelWithoutCardCompanyCancelFails() {
        Payment payment = unknownCancel("cancel-1", PART);

        payment.settleCancels(List.of(), SETTLE_BEFORE);

        assertThat(payment.findCancel("cancel-1").orElseThrow().getStatus()).isEqualTo(CancelStatus.FAILED);
    }

    @Test
    @DisplayName("이미 우리 기록에 있는 카드사 취소는 결과를 모르는 다른 취소에 다시 맞추지 않는다")
    void knownCardCompanyCancelIsNotMatchedAgain() {
        // given: tx-1 로 끝난 5000원 취소와, 결과를 모르는 5000원 취소
        Payment payment = approvedPayment();
        payment.requestCancel(request("cancel-1", PART), REQUESTED_AT);
        payment.completeCancel("cancel-1", GatewayCancel.of("tx-1", PART));
        payment.requestCancel(request("cancel-2", PART), REQUESTED_AT);
        payment.markCancelUnknown("cancel-2");

        // when: 카드사에는 tx-1 하나만 있다
        payment.settleCancels(List.of(GatewayCancel.of("tx-1", PART)), SETTLE_BEFORE);

        // then
        assertThat(payment.findCancel("cancel-2").orElseThrow().getStatus()).isEqualTo(CancelStatus.FAILED);
    }

    @Test
    @DisplayName("보정 대기 시간이 지나지 않은 취소는 카드사가 아직 처리 중일 수 있어 닫지 않는다")
    void recentCancelIsNotSettled() {
        Payment payment = unknownCancel("cancel-1", PART);

        payment.settleCancels(List.of(), REQUESTED_AT);

        assertThat(payment.findCancel("cancel-1").orElseThrow().getStatus()).isEqualTo(CancelStatus.UNKNOWN);
    }

    @Test
    @DisplayName("보정으로 남은 금액이 0원이 되면 결제는 승인 취소가 된다")
    void settleToZeroCancelsPayment() {
        Payment payment = unknownCancel("cancel-1", new BigDecimal("15000"));

        payment.settleCancels(List.of(GatewayCancel.of("tx-1", new BigDecimal("15000"))), SETTLE_BEFORE);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("보정이 먼저 닫은 취소에 카드사 답이 늦게 와도 결과를 바꾸지 않는다")
    void lateAnswerDoesNotChangeSettledCancel() {
        // given: 보정이 취소 실패로 닫은 취소
        Payment payment = unknownCancel("cancel-1", PART);
        payment.settleCancels(List.of(), SETTLE_BEFORE);

        // when
        payment.completeCancel("cancel-1", GatewayCancel.of("tx-1", PART));

        // then
        assertThat(payment.findCancel("cancel-1").orElseThrow().getStatus()).isEqualTo(CancelStatus.FAILED);
    }

    @Test
    @DisplayName("결과를 남긴 취소는 결과 모름으로 되돌리지 않는다")
    void settledCancelIsNotMarkedUnknown() {
        Payment payment = approvedPayment();
        payment.requestCancel(request("cancel-1", PART), REQUESTED_AT);
        payment.completeCancel("cancel-1", GatewayCancel.of("tx-1", PART));

        payment.markCancelUnknown("cancel-1");

        assertThat(payment.findCancel("cancel-1").orElseThrow().getStatus()).isEqualTo(CancelStatus.DONE);
    }

    private static Payment unknownCancel(String idempotencyKey, BigDecimal amount) {
        Payment payment = approvedPayment();
        payment.requestCancel(request(idempotencyKey, amount), REQUESTED_AT);
        payment.markCancelUnknown(idempotencyKey);
        return payment;
    }

    private static CancelRequest request(String idempotencyKey, BigDecimal amount) {
        return CancelRequest.of(ORDER_ID, idempotencyKey, amount, REASON);
    }

    private static Payment approvedPayment() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));
        payment.authenticate("key-1", AUTHENTICATED_AT);
        payment.approve();
        return payment;
    }
}
