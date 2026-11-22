package com.example.payment.domain.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.domain.payment.GatewayStatus;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("대사에서 우리 결제와 카드사 결제 견주기")
class PaymentMismatchTest {

    static final String KEY = "key-1";
    static final BigDecimal AMOUNT = new BigDecimal("15000");
    static final Instant AT = Instant.parse("2026-11-22T01:00:00Z");

    @Nested
    @DisplayName("둘 다 있는 결제")
    class BothSides {

        @Test
        @DisplayName("승인 금액이 다르면 금액 어긋남이다")
        void differentAmountIsAmountMismatch() {
            assertThat(typeOf(ours(PaymentStatus.APPROVED, "14000", "0"), card(approval())))
                    .contains(MismatchType.AMOUNT_MISMATCH);
        }

        @Test
        @DisplayName("우리는 승인인데 카드사는 전액 취소면 우리 기록이 뒤처진 것이다")
        void approvedButCanceledIsBehind() {
            assertThat(typeOf(ours(PaymentStatus.APPROVED, "15000", "0"), card(approval(), cancel("15000", GatewayStatus.CANCELED))))
                    .contains(MismatchType.STATUS_BEHIND);
        }

        @Test
        @DisplayName("우리는 결과를 모르는데 카드사는 승인이면 우리 기록이 뒤처진 것이다")
        void unknownButApprovedIsBehind() {
            assertThat(typeOf(ours(PaymentStatus.APPROVAL_UNKNOWN, "15000", "0"), card(approval())))
                    .contains(MismatchType.STATUS_BEHIND);
        }

        @Test
        @DisplayName("둘 다 부분 취소여도 우리 남은 금액이 더 많으면 우리 기록이 뒤처진 것이다")
        void largerBalanceIsBehind() {
            assertThat(typeOf(ours(PaymentStatus.APPROVED, "15000", "5000"),
                    card(approval(), cancel("5000", GatewayStatus.PARTIAL_CANCELED), cancel("2000", GatewayStatus.PARTIAL_CANCELED))))
                    .contains(MismatchType.STATUS_BEHIND);
        }

        @Test
        @DisplayName("우리 남은 금액이 카드사보다 적으면 운영자가 볼 상태 어긋남이다")
        void smallerBalanceIsMismatch() {
            assertThat(typeOf(ours(PaymentStatus.APPROVED, "15000", "7000"), card(approval(), cancel("5000", GatewayStatus.PARTIAL_CANCELED))))
                    .contains(MismatchType.STATUS_MISMATCH);
        }

        @Test
        @DisplayName("우리는 승인 없이 끝났는데 카드사는 승인했으면 운영자가 볼 상태 어긋남이다")
        void failedButApprovedIsMismatch() {
            assertThat(typeOf(ours(PaymentStatus.APPROVAL_FAILED, "15000", "0"), card(approval())))
                    .contains(MismatchType.STATUS_MISMATCH);
        }

        @Test
        @DisplayName("매입한 결제는 카드사 승인과 같은 단계로 본다")
        void capturedMatchesApproval() {
            assertThat(typeOf(ours(PaymentStatus.CAPTURED, "15000", "0"), card(approval()))).isEmpty();
        }

        @Test
        @DisplayName("그날 승인 거래 없이 부분 취소 거래만 있으면 남은 금액을 몰라 단계만 견준다")
        void onlyCancelThatDayComparesStage() {
            assertThat(typeOf(ours(PaymentStatus.APPROVED, "15000", "5000"), card(cancel("2000", GatewayStatus.PARTIAL_CANCELED))))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("한쪽에만 있는 결제")
    class OneSide {

        @Test
        @DisplayName("카드사 거래 내역에 없는 우리 승인 결제는 우리에게만 있는 결제다")
        void approvedWithoutTransactionIsOursOnly() {
            assertThat(typeOf(ours(PaymentStatus.APPROVED, "15000", "0"), null)).contains(MismatchType.OURS_ONLY);
        }

        @Test
        @DisplayName("카드사 거래 내역에 없는 승인 결과를 기다리는 결제는 어긋남이 아니다")
        void awaitingWithoutTransactionIsNotMismatch() {
            assertThat(typeOf(ours(PaymentStatus.APPROVAL_UNKNOWN, "15000", "0"), null)).isEmpty();
        }

        @Test
        @DisplayName("카드사 거래 내역에 없는 승인 실패 결제는 어긋남이 아니다")
        void failedWithoutTransactionIsNotMismatch() {
            assertThat(typeOf(ours(PaymentStatus.APPROVAL_FAILED, "15000", "0"), null)).isEmpty();
        }

        @Test
        @DisplayName("우리에게 없는 카드사 결제는 카드사에만 있는 결제다")
        void transactionWithoutOursIsGatewayOnly() {
            assertThat(typeOf(null, card(approval()))).contains(MismatchType.PG_ONLY);
        }

        @Test
        @DisplayName("카드사에만 있는 결제의 어긋남에는 카드사 주문번호가 남는다")
        void gatewayOnlyKeepsGatewayOrder() {
            assertThat(Mismatch.between(null, card(approval()))).hasValueSatisfying(mismatch ->
                    assertThat(mismatch.getOrderId()).isEqualTo("order-1"));
        }
    }

    @Test
    @DisplayName("찾은 어긋남은 운영자가 확인할 것으로 남는다")
    void foundMismatchNeedsReview() {
        assertThat(Mismatch.between(ours(PaymentStatus.APPROVED, "15000", "0"), null))
                .hasValueSatisfying(mismatch -> assertThat(mismatch.getResolution()).isEqualTo(Resolution.NEEDS_REVIEW));
    }

    @Test
    @DisplayName("카드사 거래는 결제 키마다 묶이고 마지막 거래의 상태가 그 결제의 상태다")
    void transactionsAreGroupedByPaymentKey() {
        GatewayPaymentDay day = card(approval(), cancel("15000", GatewayStatus.CANCELED));

        assertThat(day.getLastStatus()).isEqualTo(GatewayStatus.CANCELED);
    }

    @Test
    @DisplayName("그날 승인 거래가 있으면 카드사 남은 금액은 승인 금액에서 취소를 뺀 금액이다")
    void balanceIsApprovalMinusCancels() {
        assertThat(card(approval(), cancel("5000", GatewayStatus.PARTIAL_CANCELED)).balance())
                .hasValueSatisfying(balance -> assertThat(balance).isEqualByComparingTo("10000"));
    }

    static Optional<MismatchType> typeOf(OurPayment ours, GatewayPaymentDay card) {
        return Mismatch.between(ours, card).map(Mismatch::getType);
    }

    static OurPayment ours(PaymentStatus status, String amount, String canceled) {
        return OurPayment.of(KEY, "order-1", new BigDecimal(amount), status, new BigDecimal(canceled));
    }

    static GatewayPaymentDay card(GatewayTransaction... transactions) {
        return GatewayPaymentDay.byPaymentKey(new ArrayList<>(List.of(transactions))).get(KEY);
    }

    static GatewayTransaction approval() {
        return GatewayTransaction.of("tx-approval", KEY, "order-1", GatewayStatus.DONE, AT, AMOUNT);
    }

    static GatewayTransaction cancel(String amount, GatewayStatus after) {
        return GatewayTransaction.of("tx-" + amount + after, KEY, "order-1", after, AT.plusSeconds(60), new BigDecimal(amount));
    }
}
