package com.example.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("주문")
class OrderTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    @Nested
    @DisplayName("결제 결과가 오면 주문 상태가 정해진다")
    class Outcome {

        @ParameterizedTest(name = "결제가 {0} 이면 주문은 {1} 이다")
        @CsvSource({
                "APPROVED, PAID",
                "CAPTURED, PAID",
                "APPROVAL_FAILED, PAYMENT_FAILED",
                "EXPIRED, PAYMENT_FAILED",
                "APPROVAL_CANCELED, CANCELED",
                "CAPTURE_CANCELED, CANCELED"
        })
        void outcomeDecidesOrderStatus(PaymentOutcome outcome, OrderStatus expected) {
            assertThat(Order.paidWith("order-1", outcome, NOW).getStatus()).isEqualTo(expected);
        }

        @Test
        @DisplayName("모르는 결제 상태는 결제 결과로 받지 않는다")
        void unknownPaymentStatusIsRefused() {
            assertThatThrownBy(() -> PaymentOutcome.valueOf("AUTHENTICATED"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("주문은 나중에 받은 결제 결과를 따른다")
    class Follow {

        @Test
        @DisplayName("결제 완료 주문에 취소가 오면 주문은 취소 상태가 된다")
        void canceledAfterPaid() {
            Order order = Order.paidWith("order-1", PaymentOutcome.APPROVED, NOW);

            order.follow(PaymentOutcome.APPROVAL_CANCELED, NOW.plusSeconds(1));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @Test
        @DisplayName("취소된 주문에 늦게 온 승인이 오면 주문은 다시 결제 완료가 된다 — 옛 결과인지 가리지 않는다")
        void lateApprovalWinsOverCancel() {
            Order order = Order.paidWith("order-1", PaymentOutcome.APPROVAL_CANCELED, NOW);

            order.follow(PaymentOutcome.APPROVED, NOW.plusSeconds(1));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("주문은 마지막으로 받은 결제 결과를 기억한다")
        void remembersLastPaymentOutcome() {
            Order order = Order.paidWith("order-1", PaymentOutcome.APPROVED, NOW);

            order.follow(PaymentOutcome.APPROVAL_CANCELED, NOW.plusSeconds(1));

            assertThat(order.getPaymentStatus()).isEqualTo(PaymentOutcome.APPROVAL_CANCELED);
        }
    }
}
