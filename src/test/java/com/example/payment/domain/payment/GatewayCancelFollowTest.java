package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("카드사 취소 이력 따라가기")
class GatewayCancelFollowTest {

    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-11-16T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-16T02:00:00Z");
    private static final String ORDER_ID = "order-20261116-0001";
    private static final BigDecimal PREPARED = new BigDecimal("15000");
    private static final BigDecimal PART = new BigDecimal("5000");

    @Test
    @DisplayName("카드사에서만 일어난 부분 취소를 따라가면 남은 금액이 그만큼 준다")
    void gatewayOnlyPartialCancelReducesBalance() {
        Payment payment = approvedPayment();

        payment.follow(partiallyCanceled(GatewayCancel.of("tx-1", PART, "Console")), NOW);

        assertThat(payment.balanceAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("카드사에서만 일어난 부분 취소를 따라가도 남은 금액이 있으면 결제는 승인 상태 그대로다")
    void gatewayOnlyPartialCancelKeepsApproved() {
        Payment payment = approvedPayment();

        payment.follow(partiallyCanceled(GatewayCancel.of("tx-1", PART, "Console")), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("이미 들여온 카드사 취소를 다시 읽어도 새 취소 기록이 생기지 않는다")
    void knownGatewayCancelIsNotRecordedAgain() {
        Payment payment = approvedPayment();
        GatewayPayment cardCompany = partiallyCanceled(GatewayCancel.of("tx-1", PART, "Console"));
        payment.follow(cardCompany, NOW);

        payment.follow(cardCompany, NOW.plusSeconds(60));

        assertThat(payment.getCancels()).hasSize(1);
    }

    @Test
    @DisplayName("카드사 취소 이력에 새 취소가 더해지면 새 취소만 들여온다")
    void onlyNewGatewayCancelIsRecorded() {
        Payment payment = approvedPayment();
        GatewayCancel first = GatewayCancel.of("tx-1", PART, "Console");
        payment.follow(partiallyCanceled(first), NOW);

        payment.follow(partiallyCanceled(first, GatewayCancel.of("tx-2", PART, "Console")), NOW);

        assertThat(payment.balanceAmount()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("카드사 취소로 남은 금액이 0원이 되면 승인 취소 상태가 된다")
    void gatewayCancelsToZeroBecomeApprovalCanceled() {
        Payment payment = approvedPayment();

        payment.follow(GatewayPayment.of(GatewayStatus.CANCELED, List.of(GatewayCancel.of("tx-1", PREPARED, "Console"))), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("매입된 결제도 카드사 전액 취소를 따라가면 매입 취소 상태가 된다")
    void capturedFollowsGatewayCancel() {
        Payment payment = approvedPayment();
        payment.capture();

        payment.follow(GatewayPayment.of(GatewayStatus.CANCELED, List.of(GatewayCancel.of("tx-1", PREPARED, "Console"))), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURE_CANCELED);
    }

    @Test
    @DisplayName("카드사가 사유를 주지 않은 취소는 카드사 쪽 취소라고 적어 들여온다")
    void gatewayCancelWithoutReasonIsLabeled() {
        Payment payment = approvedPayment();

        payment.follow(partiallyCanceled(GatewayCancel.of("tx-1", PART)), NOW);

        assertThat(payment.getCancels().get(0).getReason()).isEqualTo(PaymentCancel.GATEWAY_ONLY_REASON);
    }

    private static GatewayPayment partiallyCanceled(GatewayCancel... cancels) {
        return GatewayPayment.of(GatewayStatus.PARTIAL_CANCELED, List.of(cancels));
    }

    private static Payment approvedPayment() {
        Payment payment = Payment.create(ORDER_ID, PREPARED);
        payment.authenticate("key-" + ORDER_ID, AUTHENTICATED_AT);
        payment.approve();
        return payment;
    }
}
