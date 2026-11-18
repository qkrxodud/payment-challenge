package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payment.global.error.exception.AmountMismatchException;
import com.example.payment.global.error.exception.InvalidAmountException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("상품을 담은 주문의 결제 금액(8장)")
class PaymentOrderTest {

    static final List<PaymentItem> TWO_ITEMS = List.of(
            PaymentItem.of(1L, 2, new BigDecimal("10000")),
            PaymentItem.of(2L, 1, new BigDecimal("5000")));

    @Test
    @DisplayName("결제 금액은 상품가 × 수량의 합에서 쿠폰 할인과 쓴 포인트를 뺀 금액이다")
    void amountIsPriceMinusDiscounts() {
        Payment payment = Payment.order("order-1", "member-1", TWO_ITEMS, 7L, new BigDecimal("2000"), new BigDecimal("1000"));

        assertThat(payment.getAmount()).isEqualByComparingTo("22000");
    }

    @Test
    @DisplayName("쿠폰도 포인트도 쓰지 않으면 결제 금액은 상품가 합이다")
    void amountWithoutDiscountsIsPrice() {
        Payment payment = Payment.order("order-1", "member-1", TWO_ITEMS, null, BigDecimal.ZERO, null);

        assertThat(payment.getAmount()).isEqualByComparingTo("25000");
    }

    @Test
    @DisplayName("할인과 포인트를 빼고 남는 금액이 없으면 주문을 받지 않는다")
    void nothingLeftToPayIsRefused() {
        assertThatThrownBy(() -> Payment.order("order-1", "member-1", TWO_ITEMS, 7L, new BigDecimal("20000"),
                new BigDecimal("5000")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("쓴 포인트가 0 보다 작으면 주문을 받지 않는다")
    void negativePointIsRefused() {
        assertThatThrownBy(() -> Payment.order("order-1", "member-1", TWO_ITEMS, null, BigDecimal.ZERO,
                new BigDecimal("-1000")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    @DisplayName("고객이 보낸 금액이 계산한 금액과 다르면 금액이 맞지 않는다")
    void clientAmountMustMatch() {
        Payment payment = Payment.order("order-1", "member-1", TWO_ITEMS, null, BigDecimal.ZERO, null);

        assertThatThrownBy(() -> payment.verifyAmount(new BigDecimal("1000")))
                .isInstanceOf(AmountMismatchException.class);
    }
}
