package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

import com.example.payment.global.error.exception.CancelAmountExceedsBalanceException;
import com.example.payment.global.error.exception.InvalidAmountException;
import com.example.payment.global.error.exception.InvalidPaymentTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("결제 취소 받기와 취소 상태")
class CancelCheckTest {

    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-11-16T01:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-16T02:00:00Z");
    private static final String REASON = "Customer changed their mind";
    private static final BigDecimal PART = new BigDecimal("5000");

    @Test
    @DisplayName("부분 취소 뒤 남은 금액이 있으면 결제는 승인 상태 그대로다")
    void partialCancelKeepsApproved() {
        // given: 15000원 승인된 결제
        Payment payment = approvedPayment();

        // when
        cancelDone(payment, "cancel-1", PART);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("끝난 취소로 남은 금액이 0원이 되면 승인된 결제는 승인 취소가 된다")
    void nothingLeftBecomesApprovalCanceled() {
        // given: 5000원을 부분 취소한 승인 결제
        Payment payment = approvedPayment();
        cancelDone(payment, "cancel-1", PART);

        // when
        cancelDone(payment, "cancel-2", new BigDecimal("10000"));

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("끝난 취소로 남은 금액이 0원이 되면 매입된 결제는 매입 취소가 된다")
    void nothingLeftBecomesCaptureCanceled() {
        // given: 매입된 결제
        Payment payment = approvedPayment();
        payment.capture();

        // when
        cancelDone(payment, "cancel-1", new BigDecimal("15000"));

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURE_CANCELED);
    }

    @Test
    @DisplayName("부분 취소를 두 번 하면 남은 금액은 결제 금액에서 두 취소를 뺀 값이다")
    void twoPartialCancelsAreTakenFromBalance() {
        Payment payment = approvedPayment();
        cancelDone(payment, "cancel-1", PART);

        cancelDone(payment, "cancel-2", PART);

        assertThat(payment.balanceAmount()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("취소 금액이 없으면 남은 금액 전부를 취소한다")
    void missingAmountCancelsBalance() {
        // given: 5000원을 부분 취소한 승인 결제
        Payment payment = approvedPayment();
        cancelDone(payment, "cancel-1", PART);

        // when
        BigDecimal cancelAmount = payment.requestCancel(request("cancel-2", null), REQUESTED_AT).getAmount();

        // then
        assertThat(cancelAmount).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("남은 금액 안의 취소는 받아들이고 요청한 금액을 그대로 취소한다")
    void cancelWithinBalanceIsAccepted() {
        Payment payment = approvedPayment();

        assertThat(payment.requestCancel(request("cancel-1", PART), REQUESTED_AT).getAmount()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("남은 금액보다 큰 취소는 받지 않는다")
    void cancelLargerThanBalanceIsRefused() {
        // given: 5000원을 부분 취소해 10000원이 남은 결제
        Payment payment = approvedPayment();
        cancelDone(payment, "cancel-1", PART);

        // when & then
        assertThatThrownBy(() -> payment.requestCancel(request("cancel-2", new BigDecimal("10001")), REQUESTED_AT))
                .isInstanceOf(CancelAmountExceedsBalanceException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("0원 이하의 취소 금액은 받지 않는다")
    void notPositiveAmountIsRefused(BigDecimal amount) {
        Payment payment = approvedPayment();

        assertThatThrownBy(() -> payment.requestCancel(request("cancel-1", amount), REQUESTED_AT))
                .isInstanceOf(InvalidAmountException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("paymentsWithoutApproval")
    @DisplayName("승인이 남지 않은 결제의 취소는 받지 않는다")
    void paymentWithoutApprovalIsRefused(Consumer<Payment> path) {
        // given
        Payment payment = Payment.create("order-20261116-0001", new BigDecimal("15000"));
        path.accept(payment);

        // when & then
        assertThatThrownBy(() -> payment.requestCancel(request("cancel-1", PART), REQUESTED_AT).getAmount())
                .isInstanceOf(InvalidPaymentTransitionException.class);
    }

    static Stream<Named<Consumer<Payment>>> paymentsWithoutApproval() {
        return Stream.of(
                named("승인 전 결제", payment -> { }),
                named("승인이 거절된 결제", payment -> {
                    payment.authenticate("key-1", AUTHENTICATED_AT);
                    payment.failApproval();
                }),
                named("승인 결과를 모르는 결제", payment -> {
                    payment.authenticate("key-1", AUTHENTICATED_AT);
                    payment.markApprovalUnknown();
                }),
                named("이미 전액 취소된 결제", payment -> {
                    payment.authenticate("key-1", AUTHENTICATED_AT);
                    payment.approve();
                    cancelDone(payment, "cancel-1", new BigDecimal("15000"));
                }));
    }

    private static void cancelDone(Payment payment, String idempotencyKey, BigDecimal amount) {
        payment.requestCancel(request(idempotencyKey, amount), REQUESTED_AT);
        payment.completeCancel(idempotencyKey, GatewayCancel.of("tx-" + idempotencyKey, amount));
    }

    private static CancelRequest request(String idempotencyKey, BigDecimal amount) {
        return CancelRequest.of("order-20261116-0001", idempotencyKey, amount, REASON);
    }

    private static Payment approvedPayment() {
        Payment payment = Payment.create("order-20261116-0001", new BigDecimal("15000"));
        payment.authenticate("key-1", AUTHENTICATED_AT);
        payment.approve();
        return payment;
    }
}
