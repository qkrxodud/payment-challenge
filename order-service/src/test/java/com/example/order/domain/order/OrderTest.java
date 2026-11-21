package com.example.order.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.order.domain.error.exception.UnknownPaymentStatusException;
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
            assertThat(Order.paidWith("order-1", outcome, 1L, NOW).getStatus()).isEqualTo(expected);
        }

        @Test
        @DisplayName("모르는 결제 상태는 결제 결과로 받지 않는다")
        void unknownPaymentStatusIsRefused() {
            assertThatThrownBy(() -> PaymentOutcome.from("AUTHENTICATED", "1"))
                    .isInstanceOf(UnknownPaymentStatusException.class);
        }
    }

    @Nested
    @DisplayName("주문은 나중에 받은 결제 결과를 따른다")
    class Follow {

        @Test
        @DisplayName("결제 완료 주문에 취소가 오면 주문은 취소 상태가 된다")
        void canceledAfterPaid() {
            Order order = Order.paidWith("order-1", PaymentOutcome.APPROVED, 1L, NOW);

            order.follow(PaymentOutcome.APPROVAL_CANCELED, 2L, NOW.plusSeconds(1));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        }

        @Test
        @DisplayName("마지막으로 반영한 결과보다 먼저 만든 결과는 옛 결과로 본다")
        void earlierResultIsOld() {
            Order order = Order.paidWith("order-1", PaymentOutcome.APPROVAL_CANCELED, 2L, NOW);

            assertThat(order.isAfterLastApplied(1L)).isFalse();
        }

        @Test
        @DisplayName("마지막으로 반영한 결과 그 자체도 새 결과로 보지 않는다")
        void sameResultIsNotNewer() {
            Order order = Order.paidWith("order-1", PaymentOutcome.APPROVED, 2L, NOW);

            assertThat(order.isAfterLastApplied(2L)).isFalse();
        }

        @Test
        @DisplayName("마지막으로 반영한 결과보다 뒤에 만든 결과는 새 결과다")
        void laterResultIsNewer() {
            Order order = Order.paidWith("order-1", PaymentOutcome.APPROVED, 2L, NOW);

            assertThat(order.isAfterLastApplied(3L)).isTrue();
        }

        @Test
        @DisplayName("결과를 따르면 그 결과를 마지막으로 반영한 결과로 기억한다")
        void followRemembersEvent() {
            Order order = Order.paidWith("order-1", PaymentOutcome.APPROVED, 2L, NOW);

            order.follow(PaymentOutcome.APPROVAL_CANCELED, 5L, NOW.plusSeconds(1));

            assertThat(order.isAfterLastApplied(5L)).isFalse();
        }

        @Test
        @DisplayName("주문은 마지막으로 받은 결제 결과를 기억한다")
        void remembersLastPaymentOutcome() {
            Order order = Order.paidWith("order-1", PaymentOutcome.APPROVED, 1L, NOW);

            order.follow(PaymentOutcome.APPROVAL_CANCELED, 2L, NOW.plusSeconds(1));

            assertThat(order.getPaymentStatus()).isEqualTo(PaymentOutcome.APPROVAL_CANCELED);
        }
    }
}
