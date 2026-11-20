package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.Attempt;
import com.example.payment.InventoryServerConfiguration;
import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.error.ErrorCode;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 재고를 언제 줄이는가(8장 결정 2). 준비할 때, 승인을 요청하기 직전(인증), 카드사가 승인한 뒤 가운데 어디서 줄이느냐가 구현마다 다르다.
 * 지켜야 할 것: 재고가 없는 주문은 카드사에 승인을 요청하지 않는다(고객 카드에 승인도 취소도 남지 않는다),
 * 카드사 답을 기다리는 주문이 잡은 재고는 다른 주문이 가져가지 못한다, 준비만 한 주문은 재고를 잡지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InventoryServerConfiguration.class})
abstract class HoldTimingContract extends OrderPaymentContract {

    @Test
    @DisplayName("재고가 없는 상품의 주문은 카드사에 승인을 요청하지 않는다")
    void outOfStockOrderIsNotSent() {
        PreparePaymentCommand command = order(shop.product(PRICE, 0), 1);

        orderAndConfirm(command);

        assertThat(mockPg.confirmRequestCount(command.orderId())).isZero();
    }

    @Test
    @DisplayName("재고가 없는 상품의 주문은 재고가 없다고 받지 않는다")
    void outOfStockOrderIsRefused() {
        Attempt<PaymentResult> ordered = orderAndConfirm(order(shop.product(PRICE, 0), 1));

        assertThat(ordered.errorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("재고가 없는 상품의 주문은 카드사에서 승인된 뒤 취소되는 일이 없다")
    void outOfStockOrderIsNotCanceledAtCardCompany() {
        PreparePaymentCommand command = order(shop.product(PRICE, 0), 1);

        orderAndConfirm(command);

        assertThat(mockPg.cancelCount(command.orderId())).isZero();
    }

    @Test
    @DisplayName("카드사 답을 기다리는 주문이 잡은 재고는 다른 주문이 가져가지 못한다")
    void waitingOrderKeepsStock() {
        // given: 재고 1개를 담은 첫 주문이 카드사 승인 답을 기다린다
        Long productId = shop.product(PRICE, 1);
        PreparePaymentCommand first = order(productId, 1);
        mockPg.holdConfirmAnswers();
        CompletableFuture<Attempt<PaymentResult>> waiting = Attempt.start(() -> orderAndConfirm(first).result());
        try {
            await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.approvalCount(first.orderId()) == 1);

            // when: 그동안 같은 상품을 담은 다른 주문이 온다
            CompletableFuture<Attempt<PaymentResult>> later = Attempt.start(() -> {
                Attempt<PaymentResult> ordered = orderAndConfirm(order(productId, 1));
                if (ordered.isAnswered()) {
                    return ordered.result();
                }
                throw ordered.failure();
            });

            // then: 첫 주문의 답이 오기 전에 재고가 없다고 답을 받는다
            await().atMost(Duration.ofSeconds(3)).until(later::isDone);
            assertThat(later.join().errorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK);
        } finally {
            mockPg.releaseConfirmAnswers();
            waiting.join();
        }
    }

    @Test
    @DisplayName("준비만 하고 승인을 요청하지 않은 주문은 재고를 줄이지 않는다")
    void preparedOrderDoesNotHoldStock() {
        Long productId = shop.product(PRICE, 3);

        prepare(order(productId, 1));

        assertThat(shop.stockOf(productId)).isEqualTo(3);
    }

    @Test
    @DisplayName("승인된 주문은 담은 수량만큼 재고를 줄여 둔다")
    void approvedOrderHoldsStock() {
        Long productId = shop.product(PRICE, 3);

        orderAndConfirm(order(productId, 2));

        assertThat(shop.stockOf(productId)).isEqualTo(1);
    }
}
