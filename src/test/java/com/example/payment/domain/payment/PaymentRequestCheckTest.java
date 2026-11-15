package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payment.global.error.exception.AmountMismatchException;
import com.example.payment.global.error.exception.DuplicateOrderException;
import com.example.payment.global.error.exception.InvalidAmountException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("결제 금액과 승인 요청 확인")
class PaymentRequestCheckTest {

    private static final String ORDER_ID = "order-20261115-0001";

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("금액이 없거나 0원 이하인 결제는 만들 수 없다")
    void cannotCreateWithoutPositiveAmount(BigDecimal amount) {
        assertThatThrownBy(() -> Payment.create(ORDER_ID, amount))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("승인 요청 금액이 준비한 금액과 같으면 받아들인다")
    void acceptsSameAmount() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));

        assertThatCode(() -> payment.verifyAmount(new BigDecimal("15000"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("소수 자릿수만 다르게 적은 같은 금액도 받아들인다")
    void acceptsSameAmountWrittenDifferently() {
        // DB 에서 읽은 금액은 소수 둘째 자리까지 붙어 온다
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000.00"));

        assertThatCode(() -> payment.verifyAmount(new BigDecimal("15000"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("승인 요청 금액이 준비한 금액과 다르면 받아들이지 않는다")
    void refusesDifferentAmount() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));

        assertThatThrownBy(() -> payment.verifyAmount(new BigDecimal("100")))
                .isInstanceOf(AmountMismatchException.class);
    }

    @Test
    @DisplayName("금액 없는 승인 요청은 받아들이지 않는다")
    void refusesMissingAmount() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));

        assertThatThrownBy(() -> payment.verifyAmount(null))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("같은 금액으로 다시 준비하는 요청은 받아들인다")
    void acceptsSamePreparation() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000.00"));

        assertThatCode(() -> payment.verifyPreparedAmount(new BigDecimal("15000"))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("금액이 없거나 0원 이하인 재준비 요청은 새로 준비할 때와 같이 받아들이지 않는다")
    void refusesRepreparationWithoutPositiveAmount(BigDecimal amount) {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));

        assertThatThrownBy(() -> payment.verifyPreparedAmount(amount))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("다른 금액으로 다시 준비하는 요청은 이미 있는 주문으로 본다")
    void refusesOtherPreparation() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));

        assertThatThrownBy(() -> payment.verifyPreparedAmount(new BigDecimal("20000")))
                .isInstanceOf(DuplicateOrderException.class);
    }

    @Test
    @DisplayName("승인 결과가 남은 결제는 인증할 때 받은 결제 키의 요청을 같은 요청으로 본다")
    void sameKeyAfterResultIsSameRequest() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));
        payment.authenticate("key-1");
        payment.approve();

        assertThat(payment.isConfirmedWith("key-1")).isTrue();
    }

    @Test
    @DisplayName("다른 결제 키의 요청은 같은 요청으로 보지 않는다")
    void otherKeyIsNotSameRequest() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));
        payment.authenticate("key-1");
        payment.approve();

        assertThat(payment.isConfirmedWith("key-2")).isFalse();
    }

    @Test
    @DisplayName("카드사 답을 아직 기록하지 않은 결제는 같은 결제 키라도 결과가 남은 요청으로 보지 않는다")
    void sameKeyWithoutResultIsNotConfirmed() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));
        payment.authenticate("key-1");

        assertThat(payment.isConfirmedWith("key-1")).isFalse();
    }

    @Test
    @DisplayName("결과를 모르는 결제는 같은 결제 키의 요청을 이미 카드사에 보낸 요청으로 본다")
    void sameKeyForUnknownPaymentWasAlreadySent() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));
        payment.authenticate("key-1");
        payment.markApprovalUnknown();

        assertThat(payment.isConfirmedWith("key-1")).isTrue();
    }

    @Test
    @DisplayName("인증 전 결제는 결제 키가 없어 어떤 요청도 결과가 남은 요청으로 보지 않는다")
    void readyPaymentHasNoConfirmedRequest() {
        Payment payment = Payment.create(ORDER_ID, new BigDecimal("15000"));

        assertThat(payment.isConfirmedWith(null)).isFalse();
    }
}
