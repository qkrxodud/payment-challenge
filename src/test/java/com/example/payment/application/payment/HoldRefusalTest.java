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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 8장 결정 2: 재고·쿠폰·포인트 가운데 하나라도 모자라면 인증 트랜잭션이 통째로 되돌려진다. 재고(5개)와 쿠폰(2000원)은 있지만
 * 포인트가 500 원뿐인 회원이 포인트 1000 원을 쓰는 주문(10000 − 2000 − 1000 = 7000원)이다. 잡는 순서는 재고 → 쿠폰 → 포인트라
 * 재고·쿠폰을 잡은 뒤에 포인트에서 모자란다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("잡지 못한 주문")
class HoldRefusalTest extends OrderPaymentContract {

    Long productId;
    String memberId;
    Long couponId;
    PreparePaymentCommand command;

    @BeforeEach
    void stockShop() {
        productId = shop.product(PRICE, 5);
        memberId = shop.member(new BigDecimal("500"));
        couponId = shop.coupon(memberId, new BigDecimal("2000"));
        command = order(memberId, productId, 1, couponId, new BigDecimal("1000"), new BigDecimal("7000"));
    }

    @Test
    @DisplayName("포인트가 모자란 주문은 포인트가 모자라다고 받지 않는다")
    void insufficientPointIsRefused() {
        Attempt<PaymentResult> ordered = orderAndConfirm(command);

        assertThat(ordered.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINT);
    }

    @Test
    @DisplayName("포인트가 모자라 받지 않은 주문은 먼저 잡은 재고와 쿠폰도 남기지 않는다")
    void refusedOrderLeavesStockAndCoupon() {
        orderAndConfirm(command);

        assertThat(List.of(shop.stockOf(productId), shop.couponStatusOf(couponId))).containsExactly(5, "ISSUED");
    }

    @Test
    @DisplayName("포인트가 모자라 받지 않은 주문은 카드사에 승인을 요청하지 않는다")
    void refusedOrderIsNotSent() {
        orderAndConfirm(command);

        assertThat(mockPg.confirmRequestCount(command.orderId())).isZero();
    }

    @Test
    @DisplayName("받지 않은 주문의 결제는 인증도 남지 않고 준비 상태 그대로다")
    void refusedOrderStaysReady() {
        orderAndConfirm(command);

        assertThat(paymentQueryService.find(command.orderId()).status()).isEqualTo(PaymentStatus.READY);
    }
}
