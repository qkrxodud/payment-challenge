package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.Attempt;
import com.example.payment.InventoryServerConfiguration;
import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.PaymentResult;
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
 * 같은 회원이 포인트를 겹쳐 쓸 때(8장 결정 3, 포인트도 재고와 같은 조건부 UPDATE). 포인트 1000원을 가진 회원이 각각 1000원을 쓰는
 * 두 주문(10000 − 1000 = 9000원)을 준비해 두고 승인을 요청한다. 겹침 고정: 먼저 보낸 주문이 포인트를 쓰고 인증을 남기다
 * 1초 붙잡힌 동안(SlowWrites) 두 번째 주문의 승인을 보낸다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InventoryServerConfiguration.class})
abstract class PointContract extends OrderPaymentContract {

    static final BigDecimal USE_POINT = new BigDecimal("1000");

    Long productId;
    String memberId;

    @BeforeEach
    void stockShop() {
        productId = shop.product(PRICE, 10);
        memberId = shop.member(USE_POINT);
    }

    @Test
    @DisplayName("포인트 1000원인 회원의 두 주문이 겹쳐 각각 1000원을 쓰면 한 주문만 승인된다")
    void overlappingPointOrdersApproveOne() {
        List<Attempt<PaymentResult>> answers = orderTogether();

        assertThat(answers.stream()
                .filter(Attempt::isAnswered)
                .filter(answer -> answer.result().status() == PaymentStatus.APPROVED)
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("포인트 1000원인 회원의 두 주문이 겹치면 나중 주문은 포인트가 모자라다고 받지 않는다")
    void laterPointOrderIsRefused() {
        List<Attempt<PaymentResult>> answers = orderTogether();

        assertThat(answers.get(1).errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINT);
    }

    @Test
    @DisplayName("포인트 1000원인 회원의 두 주문이 겹쳐도 포인트 잔액은 0 아래로 내려가지 않는다")
    void overlappingPointOrdersKeepBalanceNonNegative() {
        orderTogether();

        assertThat(shop.pointsOf(memberId)).isNotNegative();
    }

    // 먼저 보낸 주문이 포인트를 쓰고 인증을 남기다 붙잡힌 동안 두 번째 주문의 승인을 보낸다. 두 주문의 답을 보낸 순서대로 돌려준다
    List<Attempt<PaymentResult>> orderTogether() {
        PaymentResult first = prepare(order(memberId, productId, 1, null, USE_POINT, PRICE.subtract(USE_POINT)));
        PaymentResult later = prepare(order(memberId, productId, 1, null, USE_POINT, PRICE.subtract(USE_POINT)));
        slowWrites.holdPaymentChangeTo(PaymentStatus.AUTHENTICATED);
        CompletableFuture<Attempt<PaymentResult>> firstAnswer = Attempt.start(() -> confirm(first));
        slowWrites.awaitHeld();
        Attempt<PaymentResult> laterAnswer = Attempt.of(() -> confirm(later));
        return List.of(firstAnswer.join(), laterAnswer);
    }
}
