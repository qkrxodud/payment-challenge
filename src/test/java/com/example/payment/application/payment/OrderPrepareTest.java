package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.Attempt;
import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.OrderLine;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.global.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("상품을 담은 결제 준비")
class OrderPrepareTest extends OrderPaymentContract {

    static final BigDecimal DISCOUNT = new BigDecimal("2000");

    @Test
    @DisplayName("없는 상품을 담은 준비는 받지 않는다")
    void unknownProductIsRefused() {
        Attempt<PaymentResult> prepared = Attempt.of(() -> prepare(order(Long.MAX_VALUE, 1)));

        assertThat(prepared.errorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("수량이 0 인 상품을 담은 준비는 받지 않는다")
    void zeroQuantityIsRefused() {
        Long productId = shop.product(PRICE, 10);

        Attempt<PaymentResult> prepared = Attempt.of(() -> prepare(order(null, productId, 0, null, null, PRICE)));

        assertThat(prepared.errorCode()).isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("다른 회원의 쿠폰으로는 준비할 수 없다")
    void otherMembersCouponIsRefused() {
        Long productId = shop.product(PRICE, 10);
        Long couponId = shop.coupon(shop.member(BigDecimal.ZERO), DISCOUNT);

        Attempt<PaymentResult> prepared = Attempt.of(() -> prepare(
                order(shop.member(BigDecimal.ZERO), productId, 1, couponId, null, PRICE.subtract(DISCOUNT))));

        assertThat(prepared.errorCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("없는 쿠폰으로는 준비할 수 없다")
    void unknownCouponIsRefused() {
        Long productId = shop.product(PRICE, 10);

        Attempt<PaymentResult> prepared = Attempt.of(() -> prepare(
                order(shop.member(BigDecimal.ZERO), productId, 1, Long.MAX_VALUE, null, PRICE.subtract(DISCOUNT))));

        assertThat(prepared.errorCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("이미 결제에 쓴 쿠폰으로는 준비할 수 없다")
    void usedCouponIsRefused() {
        Long productId = shop.product(PRICE, 10);
        String memberId = shop.member(BigDecimal.ZERO);
        Long couponId = shop.coupon(memberId, DISCOUNT);
        orderAndConfirm(order(memberId, productId, 1, couponId, null, PRICE.subtract(DISCOUNT)));

        Attempt<PaymentResult> prepared = Attempt.of(() -> prepare(
                order(memberId, productId, 1, couponId, null, PRICE.subtract(DISCOUNT))));

        assertThat(prepared.errorCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("같은 주문을 같은 상품으로 다시 준비하면 처음 결제를 그대로 받는다")
    void samePrepareAgainKeepsPayment() {
        Long productId = shop.product(PRICE, 10);
        PreparePaymentCommand command = order(productId, 2);
        prepare(command);

        PaymentResult again = prepare(command);

        assertThat(again.amount()).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("같은 주문을 다른 수량으로 다시 준비하면 이미 있는 주문이라고 받지 않는다")
    void otherQuantityAgainIsRefused() {
        Long productId = shop.product(PRICE, 10);
        PreparePaymentCommand first = order(productId, 2);
        prepare(first);

        Attempt<PaymentResult> again = Attempt.of(() -> prepare(new PreparePaymentCommand(first.orderId(), PRICE, null,
                List.of(new OrderLine(productId, 1)), null, null)));

        assertThat(again.errorCode()).isEqualTo(ErrorCode.DUPLICATE_ORDER);
    }
}
