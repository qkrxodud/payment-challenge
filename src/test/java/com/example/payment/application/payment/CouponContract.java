package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.Attempt;
import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 쿠폰 하나는 결제 하나에만 쓰인다(8장 결정 5). 같은 회원이 같은 2000원 쿠폰을 담은 두 주문(8000원)을 준비해 두고 승인을 요청한다.
 * 쿠폰을 쓰는 방법(조건부 UPDATE, 읽고 확인한 뒤 저장, 사용 기록에 두 번 올라갈 수 없게)이 구현마다 다르다.
 * 겹침 고정: 먼저 보낸 주문이 쿠폰을 쓰고 인증을 남기다 1초 붙잡힌 동안(SlowWrites) 두 번째 주문의 승인을 보낸다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class CouponContract extends OrderPaymentContract {

    static final BigDecimal DISCOUNT = new BigDecimal("2000");

    Long productId;
    String memberId;
    Long couponId;

    @BeforeEach
    void stockShop() {
        productId = shop.product(PRICE, 10);
        memberId = shop.member(BigDecimal.ZERO);
        couponId = shop.coupon(memberId, DISCOUNT);
    }

    @Test
    @DisplayName("같은 쿠폰을 담은 두 주문이 겹치면 한 주문만 승인된다")
    void overlappingOrdersWithSameCouponApproveOne() {
        List<Attempt<PaymentResult>> answers = orderTogether();

        assertThat(answers.stream()
                .filter(Attempt::isAnswered)
                .filter(answer -> answer.result().status() == PaymentStatus.APPROVED)
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 쿠폰을 담은 두 주문이 겹치면 나중 주문은 쓸 수 없는 쿠폰이라고 받지 않는다")
    void laterOrderWithSameCouponIsRefused() {
        List<Attempt<PaymentResult>> answers = orderTogether();

        assertThat(answers.get(1).errorCode()).isEqualTo(ErrorCode.COUPON_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("다른 결제에 이미 쓴 쿠폰을 담은 주문은 카드사에 승인을 요청하지 않는다")
    void orderWithUsedCouponIsNotSent() {
        PaymentResult first = prepare(orderWithCoupon());
        PaymentResult later = prepare(orderWithCoupon());
        confirm(first);

        Attempt.of(() -> confirm(later));

        assertThat(mockPg.confirmRequestCount(later.orderId())).isZero();
    }

    // 먼저 보낸 주문이 쿠폰을 쓰고 인증을 남기다 붙잡힌 동안 두 번째 주문의 승인을 보낸다. 두 주문의 답을 보낸 순서대로 돌려준다
    List<Attempt<PaymentResult>> orderTogether() {
        PaymentResult first = prepare(orderWithCoupon());
        PaymentResult later = prepare(orderWithCoupon());
        slowWrites.holdPaymentChangeTo(PaymentStatus.AUTHENTICATED);
        CompletableFuture<Attempt<PaymentResult>> firstAnswer = Attempt.start(() -> confirm(first));
        slowWrites.awaitHeld();
        Attempt<PaymentResult> laterAnswer = Attempt.of(() -> confirm(later));
        return List.of(firstAnswer.join(), laterAnswer);
    }

    PreparePaymentCommand orderWithCoupon() {
        return order(memberId, productId, 1, couponId, null, PRICE.subtract(DISCOUNT));
    }
}
