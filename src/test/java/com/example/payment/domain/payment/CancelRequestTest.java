package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

import com.example.payment.domain.error.exception.IdempotencyKeyReusedException;
import com.example.payment.domain.error.exception.InvalidCancelReasonException;
import com.example.payment.domain.error.exception.InvalidIdempotencyKeyException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("같은 취소 요청 가리기")
class CancelRequestTest {

    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-11-16T01:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-16T02:00:00Z");
    private static final String ORDER_ID = "order-20261116-0001";
    private static final String REASON = "Customer changed their mind";
    private static final BigDecimal PART = new BigDecimal("5000");

    @Test
    @DisplayName("같은 Idempotency-Key 로 같은 내용이 다시 오면 이미 받은 취소로 본다")
    void sameKeyAndContentIsRepeated() {
        // given: cancel-1 로 5000원을 취소한 결제
        Payment payment = approvedPayment();
        cancelDone(payment, request("cancel-1", PART));

        // when & then
        assertThat(payment.findRepeatedCancel(request("cancel-1", PART))).isPresent();
    }

    @Test
    @DisplayName("Idempotency-Key 가 다르면 같은 금액이라도 새 취소로 본다")
    void otherKeyIsNewCancel() {
        Payment payment = approvedPayment();
        cancelDone(payment, request("cancel-1", PART));

        assertThat(payment.findRepeatedCancel(request("cancel-2", PART))).isEmpty();
    }

    @Test
    @DisplayName("같은 Idempotency-Key 에 다른 금액이 오면 받지 않는다")
    void sameKeyWithOtherAmountIsRefused() {
        Payment payment = approvedPayment();
        cancelDone(payment, request("cancel-1", PART));

        assertThatThrownBy(() -> payment.findRepeatedCancel(request("cancel-1", new BigDecimal("3000"))))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 에 다른 사유가 오면 받지 않는다")
    void sameKeyWithOtherReasonIsRefused() {
        Payment payment = approvedPayment();
        cancelDone(payment, request("cancel-1", PART));

        assertThatThrownBy(() -> payment.findRepeatedCancel(
                CancelRequest.of(ORDER_ID, "cancel-1", PART, "Item was out of stock")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    @DisplayName("소수 자릿수만 다르게 적은 같은 금액은 같은 내용으로 본다")
    void sameAmountWrittenDifferentlyIsSameContent() {
        Payment payment = approvedPayment();
        cancelDone(payment, request("cancel-1", PART));

        assertThat(payment.findRepeatedCancel(request("cancel-1", new BigDecimal("5000.00")))).isPresent();
    }

    @Test
    @DisplayName("금액 없이 온 취소는 그사이 남은 금액이 줄었어도 같은 키로 금액 없이 다시 오면 같은 내용으로 본다")
    void missingAmountIsComparedAsWritten() {
        // given: 금액 없이 cancel-1 로 남은 금액 전부를 취소한 결제
        Payment payment = approvedPayment();
        CancelRequest first = request("cancel-1", null);
        cancelDone(payment, first);

        // when & then
        assertThat(payment.findRepeatedCancel(request("cancel-1", null))).isPresent();
    }

    @Test
    @DisplayName("이미 받은 취소에는 그 취소가 끝났을 때 남은 금액이 남아 있어, 뒤에 다른 취소가 있어도 바뀌지 않는다")
    void repeatedCancelKeepsBalanceOfThatTime() {
        // given: cancel-1 로 5000원, cancel-2 로 5000원을 취소한 결제
        Payment payment = approvedPayment();
        cancelDone(payment, request("cancel-1", PART));
        cancelDone(payment, request("cancel-2", PART));

        // when
        PaymentCancel repeated = payment.findRepeatedCancel(request("cancel-1", PART)).orElseThrow();

        // then
        assertThat(repeated.getBalanceAfter()).isEqualByComparingTo("10000");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidKeys")
    @DisplayName("Idempotency-Key 가 없거나 300자를 넘으면 취소를 받지 않는다")
    void invalidKeyIsRefused(String idempotencyKey) {
        assertThatThrownBy(() -> CancelRequest.of(ORDER_ID, idempotencyKey, PART, REASON))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
    }

    @Test
    @DisplayName("300자인 Idempotency-Key 는 받는다")
    void keyOfMaxLengthIsAccepted() {
        assertThatCode(() -> CancelRequest.of(ORDER_ID, "k".repeat(300), PART, REASON)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    @DisplayName("취소 사유가 비어 있으면 받지 않는다")
    void blankReasonIsRefused(String reason) {
        assertThatThrownBy(() -> CancelRequest.of(ORDER_ID, "cancel-1", PART, reason))
                .isInstanceOf(InvalidCancelReasonException.class);
    }

    @Test
    @DisplayName("취소 사유가 없으면 받지 않는다")
    void missingReasonIsRefused() {
        assertThatThrownBy(() -> CancelRequest.of(ORDER_ID, "cancel-1", PART, null))
                .isInstanceOf(InvalidCancelReasonException.class);
    }

    static Stream<Named<String>> invalidKeys() {
        return Stream.of(
                named("키가 없을 때", null),
                named("키가 비어 있을 때", " "),
                named("301자일 때", "k".repeat(301)));
    }

    private static void cancelDone(Payment payment, CancelRequest request) {
        PaymentCancel cancel = payment.requestCancel(request, REQUESTED_AT);
        payment.completeCancel(request.getIdempotencyKey(), GatewayCancel.of("tx-" + request.getIdempotencyKey(), cancel.getAmount()));
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
