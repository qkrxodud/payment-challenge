package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("카드사에서 다시 읽은 결제 상태 따라가기")
class PaymentFollowTest {

    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-11-16T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-16T01:00:05Z");
    private static final String ORDER_ID = "order-20261116-0001";
    private static final BigDecimal PREPARED = new BigDecimal("15000");

    @Test
    @DisplayName("결과 모름 결제는 카드사에 승인이 남아 있으면 승인된다")
    void approvalUnknownFollowsApproval() {
        Payment payment = approvalUnknownPayment();

        payment.follow(cardCompany(GatewayStatus.DONE), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("승인 답을 기다리는 결제도 카드사에 승인이 남아 있으면 승인된다")
    void authenticatedFollowsApproval() {
        Payment payment = authenticatedPayment();

        payment.follow(cardCompany(GatewayStatus.DONE), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("결과 모름 결제는 카드사가 거절했으면 승인 실패로 닫힌다")
    void approvalUnknownFollowsDecline() {
        Payment payment = approvalUnknownPayment();

        payment.follow(cardCompany(GatewayStatus.ABORTED), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    @Test
    @DisplayName("결과 모름 결제는 카드사가 승인한 뒤 전액 취소했으면 승인 취소 상태가 된다")
    void approvalUnknownFollowsApprovalThenCancel() {
        Payment payment = approvalUnknownPayment();

        payment.follow(GatewayPayment.of(GatewayStatus.CANCELED, List.of(GatewayCancel.of("tx-1", PREPARED, "Console"))), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_CANCELED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("undecided")
    @DisplayName("결과 모름 결제는 카드사에서도 결과가 나지 않았으면 그대로 둔다")
    void approvalUnknownStaysWhileUndecided(GatewayPayment gatewayPayment) {
        Payment payment = approvalUnknownPayment();

        payment.follow(gatewayPayment, NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_UNKNOWN);
    }

    @Test
    @DisplayName("이미 승인된 결제에 카드사 승인을 다시 읽어도 승인 그대로다")
    void approvedStaysApproved() {
        Payment payment = approvedPayment();

        payment.follow(cardCompany(GatewayStatus.DONE), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("이미 지난 상태를 읽으면 되돌리지 않는다 — 승인 취소된 결제는 카드사 승인 상태를 읽어도 승인 취소 그대로다")
    void canceledIsNotTurnedBack() {
        Payment payment = approvedPayment();
        payment.cancel();

        payment.follow(cardCompany(GatewayStatus.DONE), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("승인 실패로 닫힌 결제는 카드사 승인을 읽어도 허용된 전이가 없어 그대로 둔다")
    void failedApprovalIsNotChanged() {
        Payment payment = approvalUnknownPayment();
        payment.failApproval();

        payment.follow(cardCompany(GatewayStatus.DONE), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    static Stream<Named<GatewayPayment>> undecided() {
        return Stream.of(
                named("카드사에 기록이 없을 때", GatewayPayment.notFound()),
                named("카드사도 처리 중일 때", cardCompany(GatewayStatus.IN_PROGRESS)));
    }

    private static GatewayPayment cardCompany(GatewayStatus status) {
        return GatewayPayment.of(status, List.of());
    }

    private static Payment authenticatedPayment() {
        Payment payment = Payment.create(ORDER_ID, PREPARED);
        payment.authenticate("key-" + ORDER_ID, AUTHENTICATED_AT);
        return payment;
    }

    private static Payment approvalUnknownPayment() {
        Payment payment = authenticatedPayment();
        payment.markApprovalUnknown();
        return payment;
    }

    private static Payment approvedPayment() {
        Payment payment = authenticatedPayment();
        payment.approve();
        return payment;
    }
}
