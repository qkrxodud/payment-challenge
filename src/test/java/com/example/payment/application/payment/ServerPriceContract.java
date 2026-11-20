package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.Attempt;
import com.example.payment.InventoryServerConfiguration;
import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.error.ErrorCode;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 결제 금액을 누가 정하는가(8장 결정 1). 상품 두 개(10000원 × 2)에 2000원 쿠폰과 포인트 1000원을 쓰면 결제 금액은 17000원이다.
 * 고객이 보낸 금액을 믿는 구현과 서버가 계산하는 구현을 같은 테스트로 돌린다. 승인 때는 3장 규칙대로 결제에 남은 금액과 비교한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InventoryServerConfiguration.class})
abstract class ServerPriceContract extends OrderPaymentContract {

    static final BigDecimal DISCOUNT = new BigDecimal("2000");
    static final BigDecimal USE_POINT = new BigDecimal("1000");
    static final BigDecimal PAYABLE = new BigDecimal("17000");
    static final BigDecimal LOWERED = new BigDecimal("1000");

    Long productId;
    String memberId;
    Long couponId;

    @BeforeEach
    void stockShop() {
        productId = shop.product(PRICE, 10);
        memberId = shop.member(new BigDecimal("5000"));
        couponId = shop.coupon(memberId, DISCOUNT);
    }

    @Test
    @DisplayName("상품을 담아 준비하면 결제 금액은 상품가 합에서 쿠폰 할인과 쓴 포인트를 뺀 금액이다")
    void preparedAmountIsPriceMinusDiscounts() {
        PaymentResult prepared = prepare(orderOf(PAYABLE));

        assertThat(prepared.amount()).isEqualByComparingTo(PAYABLE);
    }

    @Test
    @DisplayName("고객이 보낸 금액이 상품가·쿠폰·포인트로 계산한 금액과 다르면 준비를 받지 않는다")
    void differentAmountIsRefused() {
        Attempt<PaymentResult> prepared = Attempt.of(() -> prepare(orderOf(LOWERED)));

        assertThat(prepared.errorCode()).isEqualTo(ErrorCode.AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("금액을 낮춰 보낸 주문으로 승인을 요청해도 카드사에서 낮춘 금액이 승인되지 않는다")
    void loweredAmountIsNotApproved() {
        PreparePaymentCommand lowered = orderOf(LOWERED);

        Attempt.of(() -> prepare(lowered));
        Attempt.of(() -> confirm(new ConfirmPaymentCommand(lowered.orderId(), keyOf(lowered.orderId()), LOWERED)));

        // 금액은 값으로 비교한다(결제에 남은 1000.00 과 1000 은 같은 금액)
        assertThat(mockPg.approvedAmounts(lowered.orderId()))
                .usingElementComparator(BigDecimal::compareTo)
                .doesNotContain(LOWERED);
    }

    @Test
    @DisplayName("상품을 담지 않고 금액만 보낸 준비는 보낸 금액으로 준비된다")
    void amountOnlyPrepareKeepsAmount() {
        PaymentResult prepared = prepare(new PreparePaymentCommand("order-" + UUID.randomUUID(), new BigDecimal("15000")));

        assertThat(prepared.amount()).isEqualByComparingTo("15000");
    }

    PreparePaymentCommand orderOf(BigDecimal amount) {
        return order(memberId, productId, 2, couponId, USE_POINT, amount);
    }
}
