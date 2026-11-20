package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.Attempt;
import com.example.payment.InventoryServerConfiguration;
import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.payment.PaymentStatus;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 같은 상품에 주문이 몰릴 때 재고를 어떻게 줄이는가(8장 결정 3). 재고 줄은 상품마다 하나라 같은 상품의 모든 주문이 한 줄을 바꾼다.
 * 줄이는 방법(조건부 UPDATE, select … for update, 변경 번호 + 다시 시도, 잠그지 않음)이 구현마다 다르므로 구현마다 상속해 같은 테스트를 돌린다.
 * 겹침 고정: 재고 1개에 주문 둘. 먼저 보낸 주문이 재고를 줄이다 1초 붙잡히고(재고 줄의 잠금을 쥔 채),
 * 그것을 확인한 뒤 두 번째 주문의 승인을 보낸다. 10장부터 재고 줄은 재고 서비스에 있어, 붙잡는 기록도 재고 서비스의 재고 줄이기다
 * (8장에는 결제 서버가 같은 트랜잭션에서 재고를 줄인 뒤 남기는 인증을 붙잡았다). 줄이는 방법은 재고 서비스 안에서 바꿔 끼운다. 한꺼번에: 재고 10개에 주문 30건을 스레드 30개에서 한꺼번에 보낸다(겹침을 고정하지 않음).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InventoryServerConfiguration.class})
abstract class StockContract extends OrderPaymentContract {

    static final int STOCK = 10;
    static final int ORDERS = 30;

    @Test
    @DisplayName("재고 1개에 두 주문이 겹치면 한 주문만 승인된다")
    void overlappingOrdersApproveOne() {
        List<Attempt<PaymentResult>> answers = orderTogether(shop.product(PRICE, 1));

        assertThat(approvedCountOf(answers)).isEqualTo(1);
    }

    @Test
    @DisplayName("재고 1개에 두 주문이 겹치면 나중 주문은 재고가 없다고 받지 않는다")
    void laterOverlappingOrderIsRefused() {
        List<Attempt<PaymentResult>> answers = orderTogether(shop.product(PRICE, 1));

        assertThat(answers.get(1).errorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("재고 1개에 두 주문이 겹쳐도 카드사 승인은 한 건이다")
    void overlappingOrdersAreApprovedOnceAtCardCompany() {
        Long productId = shop.product(PRICE, 1);
        PreparePaymentCommand first = order(productId, 1);
        PreparePaymentCommand later = order(productId, 1);

        orderTogether(first, later);

        assertThat(mockPg.approvalCount(first.orderId()) + mockPg.approvalCount(later.orderId())).isEqualTo(1);
    }

    @Test
    @DisplayName("재고 10개에 주문 30건이 한꺼번에 와도 10건만 승인된다")
    void burstApprovesOnlyStock() {
        List<Attempt<PaymentResult>> answers = orderAtOnce(shop.product(PRICE, STOCK));

        assertThat(approvedCountOf(answers)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("재고 10개에 주문 30건이 한꺼번에 와도 남은 재고는 승인된 주문 수만큼만 줄어 있다")
    void burstLeavesStockMatchingApprovals() {
        Long productId = shop.product(PRICE, STOCK);

        List<Attempt<PaymentResult>> answers = orderAtOnce(productId);

        assertThat((long) shop.stockOf(productId)).isEqualTo(STOCK - approvedCountOf(answers));
    }

    @Test
    @DisplayName("재고 10개에 주문 30건이 한꺼번에 와도 재고는 0 아래로 내려가지 않는다")
    void burstKeepsStockNonNegative() {
        Long productId = shop.product(PRICE, STOCK);

        orderAtOnce(productId);

        assertThat(shop.stockOf(productId)).isNotNegative();
    }

    List<Attempt<PaymentResult>> orderTogether(Long productId) {
        return orderTogether(order(productId, 1), order(productId, 1));
    }

    // 먼저 보낸 주문이 재고를 줄이고 인증을 남기다 붙잡힌 동안 두 번째 주문의 승인을 보낸다. 두 주문의 답을 보낸 순서대로 돌려준다
    List<Attempt<PaymentResult>> orderTogether(PreparePaymentCommand first, PreparePaymentCommand later) {
        PaymentResult firstPrepared = prepare(first);
        PaymentResult laterPrepared = prepare(later);
        inventory.holdStockDecrease();
        CompletableFuture<Attempt<PaymentResult>> firstAnswer = Attempt.start(() -> confirm(firstPrepared));
        inventory.awaitHeld();
        Attempt<PaymentResult> laterAnswer = Attempt.of(() -> confirm(laterPrepared));
        return List.of(firstAnswer.join(), laterAnswer);
    }

    // 준비해 둔 주문 30건의 승인을 스레드 30개에서 한꺼번에 보낸다
    List<Attempt<PaymentResult>> orderAtOnce(Long productId) {
        List<PaymentResult> prepared = IntStream.range(0, ORDERS)
                .mapToObj(index -> prepare(order(productId, 1)))
                .toList();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(ORDERS);
        try {
            List<CompletableFuture<Attempt<PaymentResult>>> answers = prepared.stream()
                    .map(payment -> Attempt.start(() -> {
                        awaitStart(start);
                        return confirm(payment);
                    }, executor))
                    .toList();
            start.countDown();
            return answers.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdown();
        }
    }

    static long approvedCountOf(List<Attempt<PaymentResult>> answers) {
        return answers.stream()
                .filter(Attempt::isAnswered)
                .filter(answer -> answer.result().status() == PaymentStatus.APPROVED)
                .count();
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to start.", interrupted);
        }
    }
}
