package com.example.payment.domain.payment;

import static com.example.payment.domain.payment.PaymentAction.APPROVE;
import static com.example.payment.domain.payment.PaymentAction.AUTHENTICATE;
import static com.example.payment.domain.payment.PaymentAction.CANCEL;
import static com.example.payment.domain.payment.PaymentAction.CAPTURE;
import static com.example.payment.domain.payment.PaymentAction.EXPIRE;
import static com.example.payment.domain.payment.PaymentAction.FAIL_APPROVAL;
import static com.example.payment.domain.payment.PaymentAction.FAIL_AUTHENTICATION;
import static com.example.payment.domain.payment.PaymentAction.MARK_APPROVAL_UNKNOWN;
import static com.example.payment.domain.payment.PaymentStatus.APPROVAL_CANCELED;
import static com.example.payment.domain.payment.PaymentStatus.APPROVAL_FAILED;
import static com.example.payment.domain.payment.PaymentStatus.APPROVAL_UNKNOWN;
import static com.example.payment.domain.payment.PaymentStatus.APPROVED;
import static com.example.payment.domain.payment.PaymentStatus.AUTHENTICATED;
import static com.example.payment.domain.payment.PaymentStatus.AUTH_FAILED;
import static com.example.payment.domain.payment.PaymentStatus.CAPTURED;
import static com.example.payment.domain.payment.PaymentStatus.CAPTURE_CANCELED;
import static com.example.payment.domain.payment.PaymentStatus.EXPIRED;
import static com.example.payment.domain.payment.PaymentStatus.READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payment.domain.error.exception.InvalidPaymentTransitionException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 결제 상태 전이 규칙. 구현 방식(허용 전이 표, State 패턴, Spring Statemachine)과 상관없이
 * 같은 규칙을 지켜야 하므로, 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 */
abstract class PaymentTransitionContract {

    /** 허용되는 전이만 적는다. 여기 없는 (상태, 행위) 조합은 모두 거부되어야 한다. */
    static final Map<PaymentStatus, Map<PaymentAction, PaymentStatus>> ALLOWED = Map.of(
            READY, Map.of(
                    AUTHENTICATE, AUTHENTICATED,
                    FAIL_AUTHENTICATION, AUTH_FAILED,
                    EXPIRE, EXPIRED),
            AUTHENTICATED, Map.of(
                    APPROVE, APPROVED,
                    FAIL_APPROVAL, APPROVAL_FAILED,
                    MARK_APPROVAL_UNKNOWN, APPROVAL_UNKNOWN,
                    EXPIRE, EXPIRED),
            APPROVAL_UNKNOWN, Map.of(
                    APPROVE, APPROVED,
                    FAIL_APPROVAL, APPROVAL_FAILED),
            APPROVED, Map.of(
                    CAPTURE, CAPTURED,
                    CANCEL, APPROVAL_CANCELED),
            CAPTURED, Map.of(
                    CANCEL, CAPTURE_CANCELED));

    /** 각 상태에 도달하는 경로 */
    static final Map<PaymentStatus, List<PaymentAction>> PATH = Map.of(
            READY, List.of(),
            AUTHENTICATED, List.of(AUTHENTICATE),
            AUTH_FAILED, List.of(FAIL_AUTHENTICATION),
            APPROVED, List.of(AUTHENTICATE, APPROVE),
            APPROVAL_FAILED, List.of(AUTHENTICATE, FAIL_APPROVAL),
            APPROVAL_UNKNOWN, List.of(AUTHENTICATE, MARK_APPROVAL_UNKNOWN),
            EXPIRED, List.of(EXPIRE),
            CAPTURED, List.of(AUTHENTICATE, APPROVE, CAPTURE),
            APPROVAL_CANCELED, List.of(AUTHENTICATE, APPROVE, CANCEL),
            CAPTURE_CANCELED, List.of(AUTHENTICATE, APPROVE, CAPTURE, CANCEL));

    /** 구현마다 다른 결제를 같은 방식으로 다루기 위한 손잡이 */
    interface PaymentHandle {
        PaymentStatus status();

        void perform(PaymentAction action);
    }

    protected abstract PaymentHandle newPayment();

    @Test
    @DisplayName("승인 전에는 매입할 수 없다")
    void cannotCaptureBeforeApproval() {
        // given: 인증만 끝난 결제
        PaymentHandle payment = paymentIn(AUTHENTICATED);

        // when & then
        assertThatThrownBy(() -> payment.perform(CAPTURE))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(payment.status()).isEqualTo(AUTHENTICATED);
    }

    @Test
    @DisplayName("취소된 결제는 매입할 수 없다")
    void cannotCaptureCanceledPayment() {
        // given: 승인 후 취소된 결제
        PaymentHandle payment = paymentIn(APPROVAL_CANCELED);

        // when & then
        assertThatThrownBy(() -> payment.perform(CAPTURE))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(payment.status()).isEqualTo(APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("취소된 결제는 다시 승인할 수 없다")
    void cannotApproveCanceledPayment() {
        // given: 승인 후 취소된 결제
        PaymentHandle payment = paymentIn(APPROVAL_CANCELED);

        // when & then
        assertThatThrownBy(() -> payment.perform(APPROVE))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(payment.status()).isEqualTo(APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("승인만 된 결제를 취소하면 승인 취소가 된다")
    void cancelBeforeCaptureBecomesApprovalCancel() {
        // given: 매입 전 결제
        PaymentHandle payment = paymentIn(APPROVED);

        // when
        payment.perform(CANCEL);

        // then
        assertThat(payment.status()).isEqualTo(APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("매입까지 끝난 결제를 취소하면 매입 취소가 된다")
    void cancelAfterCaptureBecomesCaptureCancel() {
        // given: 매입이 끝난 결제
        PaymentHandle payment = paymentIn(CAPTURED);

        // when
        payment.perform(CANCEL);

        // then
        assertThat(payment.status()).isEqualTo(CAPTURE_CANCELED);
    }

    @Test
    @DisplayName("인증에 실패한 결제는 승인할 수 없다")
    void cannotApproveAfterAuthenticationFailure() {
        // given: 인증에 실패한 결제
        PaymentHandle payment = paymentIn(AUTH_FAILED);

        // when & then
        assertThatThrownBy(() -> payment.perform(APPROVE))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(payment.status()).isEqualTo(AUTH_FAILED);
    }

    @Test
    @DisplayName("승인이 거절된 결제는 매입할 수 없다")
    void cannotCaptureAfterApprovalFailure() {
        // given: 승인이 거절된 결제
        PaymentHandle payment = paymentIn(APPROVAL_FAILED);

        // when & then
        assertThatThrownBy(() -> payment.perform(CAPTURE))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(payment.status()).isEqualTo(APPROVAL_FAILED);
    }

    @Test
    @DisplayName("승인을 요청했지만 카드사 답을 받지 못한 결제는 결과 모름 상태가 된다")
    void noAnswerBecomesApprovalUnknown() {
        // given: 인증을 마치고 승인을 요청한 결제
        PaymentHandle payment = paymentIn(AUTHENTICATED);

        // when
        payment.perform(MARK_APPROVAL_UNKNOWN);

        // then
        assertThat(payment.status()).isEqualTo(APPROVAL_UNKNOWN);
    }

    @Test
    @DisplayName("결과 모름 결제는 카드사가 승인했다고 확인되면 승인 상태가 된다")
    void unknownBecomesApprovedWhenCardCompanyApproved() {
        // given: 카드사 답을 받지 못한 결제
        PaymentHandle payment = paymentIn(APPROVAL_UNKNOWN);

        // when
        payment.perform(APPROVE);

        // then
        assertThat(payment.status()).isEqualTo(APPROVED);
    }

    @Test
    @DisplayName("결과 모름 결제는 카드사에 승인이 없다고 확인되면 승인 실패로 닫힌다")
    void unknownBecomesFailedWhenCardCompanyHasNoApproval() {
        // given: 카드사 답을 받지 못한 결제
        PaymentHandle payment = paymentIn(APPROVAL_UNKNOWN);

        // when
        payment.perform(FAIL_APPROVAL);

        // then
        assertThat(payment.status()).isEqualTo(APPROVAL_FAILED);
    }

    @Test
    @DisplayName("결과 모름 결제는 매입할 수 없다")
    void cannotCaptureUnknownPayment() {
        // given: 카드사 답을 받지 못한 결제
        PaymentHandle payment = paymentIn(APPROVAL_UNKNOWN);

        // when & then
        assertThatThrownBy(() -> payment.perform(CAPTURE))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(payment.status()).isEqualTo(APPROVAL_UNKNOWN);
    }

    @Test
    @DisplayName("결과 모름 결제는 카드사에 승인이 남았을 수 있어 유효 시간이 지나도 만료로 닫지 않는다")
    void cannotExpireUnknownPayment() {
        // given: 카드사 답을 받지 못한 결제
        PaymentHandle payment = paymentIn(APPROVAL_UNKNOWN);

        // when & then
        assertThatThrownBy(() -> payment.perform(EXPIRE))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(payment.status()).isEqualTo(APPROVAL_UNKNOWN);
    }

    @ParameterizedTest(name = "{0} 상태에서 {1} 요청")
    @MethodSource("allCombinations")
    @DisplayName("결제는 허용 표에 있는 다음 단계로만 넘어가고, 표에 없는 요청은 받지 않는다")
    void followsTransitionTable(PaymentStatus from, PaymentAction action) {
        // given
        PaymentHandle payment = paymentIn(from);
        PaymentStatus expected = ALLOWED.getOrDefault(from, Map.of()).get(action);

        // when & then
        if (isAllowed(expected)) {
            payment.perform(action);
            assertThat(payment.status()).isEqualTo(expected);
            return;
        }
        assertThatThrownBy(() -> payment.perform(action))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(payment.status()).isEqualTo(from);
    }

    static Stream<Arguments> allCombinations() {
        List<Arguments> combinations = new ArrayList<>();
        for (PaymentStatus status : PaymentStatus.values()) {
            for (PaymentAction action : PaymentAction.values()) {
                combinations.add(Arguments.of(status, action));
            }
        }
        return combinations.stream();
    }

    private PaymentHandle paymentIn(PaymentStatus status) {
        PaymentHandle payment = newPayment();
        PATH.get(status).forEach(payment::perform);
        assertThat(payment.status()).as("%s까지 가는 경로", status).isEqualTo(status);
        return payment;
    }

    private boolean isAllowed(PaymentStatus expected) {
        return Objects.nonNull(expected);
    }
}
