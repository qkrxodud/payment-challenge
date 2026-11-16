package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 4장 보정이 카드사에서 다시 읽은 결제로 결과 모름 결제를 닫는 규칙. 6장부터 웹훅과 같은 따라가기를 쓴다 */
@DisplayName("카드사에 확인한 결과로 결제 닫기")
class PaymentSettleTest {

    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-11-15T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-15T01:02:00Z");
    private static final BigDecimal PREPARED = new BigDecimal("15000");

    @Test
    @DisplayName("카드사에 승인이 남아 있으면 결과 모름 결제는 승인 상태가 된다")
    void approvedByCardCompany() {
        // given: 카드사 답을 받지 못한 결제
        Payment payment = unknownPayment();

        // when
        payment.reconcile(cardCompany(GatewayStatus.DONE), NOW);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("카드사가 거절했으면 결과 모름 결제는 승인 실패로 닫힌다")
    void declinedByCardCompany() {
        Payment payment = unknownPayment();

        payment.reconcile(cardCompany(GatewayStatus.ABORTED), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    @Test
    @DisplayName("카드사에 기록이 없으면 결과 모름 결제는 승인 요청이 닿지 않은 것으로 보고 승인 실패로 닫힌다")
    void notFoundAtCardCompany() {
        Payment payment = unknownPayment();

        payment.reconcile(GatewayPayment.notFound(), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    @Test
    @DisplayName("카드사도 아직 처리 중이면 결과 모름 그대로 둔다")
    void undecidedByCardCompany() {
        Payment payment = unknownPayment();

        payment.reconcile(cardCompany(GatewayStatus.IN_PROGRESS), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_UNKNOWN);
    }

    @Test
    @DisplayName("카드사에서 승인 뒤 전액 취소된 결과 모름 결제는 승인 취소 상태가 된다")
    void canceledAfterApprovalBecomesApprovalCanceled() {
        Payment payment = unknownPayment();

        payment.reconcile(GatewayPayment.of(GatewayStatus.CANCELED, List.of(GatewayCancel.of("tx-1", PREPARED, "Console"))), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("카드사에서 승인 뒤 부분 취소된 결과 모름 결제는 승인되고 남은 금액이 카드사 잔액과 같다")
    void partiallyCanceledAfterApprovalKeepsCardCompanyBalance() {
        Payment payment = unknownPayment();

        payment.reconcile(GatewayPayment.of(GatewayStatus.PARTIAL_CANCELED,
                List.of(GatewayCancel.of("tx-1", new BigDecimal("5000"), "Console"))), NOW);

        assertThat(payment.balanceAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("결과를 기록하지 못하고 인증 상태에 남은 결제도 카드사에서 승인 뒤 취소됐으면 승인 취소 상태가 된다")
    void authenticatedCanceledAfterApprovalBecomesApprovalCanceled() {
        Payment payment = authenticatedPayment();

        payment.reconcile(GatewayPayment.of(GatewayStatus.CANCELED, List.of(GatewayCancel.of("tx-1", PREPARED, "Console"))), NOW);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_CANCELED);
    }

    private static GatewayPayment cardCompany(GatewayStatus status) {
        return GatewayPayment.of(status, List.of());
    }

    private static Payment authenticatedPayment() {
        Payment payment = Payment.create("order-20261115-0001", PREPARED);
        payment.authenticate("key-1", AUTHENTICATED_AT);
        return payment;
    }

    private static Payment unknownPayment() {
        Payment payment = authenticatedPayment();
        payment.markApprovalUnknown();
        return payment;
    }
}
