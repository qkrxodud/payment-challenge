package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("잡아 둔 재고·포인트·쿠폰을 돌려줄 때(8장)")
class PaymentReleaseTest {

    static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    @Test
    @DisplayName("인증한 결제의 승인이 거절되면 돌려줄 때다")
    void declinedAfterAuthenticationReleases() {
        Payment payment = authenticated();

        payment.failApproval();

        assertThat(payment.releasesHoldingsFrom(PaymentStatus.AUTHENTICATED)).isTrue();
    }

    @Test
    @DisplayName("인증한 결제가 승인 요청 없이 유효 시간이 지나 만료되면 돌려줄 때다")
    void expiredAfterAuthenticationReleases() {
        Payment payment = authenticated();

        payment.expire();

        assertThat(payment.releasesHoldingsFrom(PaymentStatus.AUTHENTICATED)).isTrue();
    }

    @Test
    @DisplayName("준비만 한 결제가 만료되면 잡은 것이 없어 돌려줄 때가 아니다")
    void expiredBeforeAuthenticationDoesNotRelease() {
        Payment payment = order();

        payment.expire();

        assertThat(payment.releasesHoldingsFrom(PaymentStatus.READY)).isFalse();
    }

    @Test
    @DisplayName("승인 결과를 모르게 되면 카드사에 승인이 남았을 수 있어 돌려줄 때가 아니다")
    void unknownDoesNotRelease() {
        Payment payment = authenticated();

        payment.markApprovalUnknown();

        assertThat(payment.releasesHoldingsFrom(PaymentStatus.AUTHENTICATED)).isFalse();
    }

    @Test
    @DisplayName("결과를 모르던 결제가 승인 실패로 닫히면 돌려줄 때다")
    void unknownThenDeclinedReleases() {
        Payment payment = authenticated();
        payment.markApprovalUnknown();

        payment.failApproval();

        assertThat(payment.releasesHoldingsFrom(PaymentStatus.APPROVAL_UNKNOWN)).isTrue();
    }

    @Test
    @DisplayName("승인되면 돌려줄 때가 아니다")
    void approvedDoesNotRelease() {
        Payment payment = authenticated();

        payment.approve();

        assertThat(payment.releasesHoldingsFrom(PaymentStatus.AUTHENTICATED)).isFalse();
    }

    @Test
    @DisplayName("승인된 결제가 취소 상태가 되면 돌려줄 때다")
    void canceledAfterApprovalReleases() {
        Payment payment = authenticated();
        payment.approve();

        payment.cancel();

        assertThat(payment.releasesHoldingsFrom(PaymentStatus.APPROVED)).isTrue();
    }

    @Test
    @DisplayName("이미 승인 실패로 닫힌 결제를 다시 보면 돌려줄 때가 아니다")
    void alreadyClosedDoesNotReleaseAgain() {
        Payment payment = authenticated();
        payment.failApproval();

        assertThat(payment.releasesHoldingsFrom(PaymentStatus.APPROVAL_FAILED)).isFalse();
    }

    static Payment order() {
        return Payment.order("order-1", "member-1", List.of(PaymentItem.of(1L, 2, new BigDecimal("10000"))), 7L,
                new BigDecimal("2000"), new BigDecimal("1000"));
    }

    static Payment authenticated() {
        Payment payment = order();
        payment.authenticate("key-1", NOW);
        return payment;
    }
}
