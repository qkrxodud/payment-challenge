package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.Attempt;
import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.application.payment.dto.ReceiveWebhookCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 결제가 승인 없이 끝나면 잡아 둔 재고·포인트·쿠폰을 돌려주는가(8장 결정 4). 상품 두 개(재고 5개에서 2개), 쿠폰(2000원), 포인트 1000원(잔액 3000원)을
 * 쓴 17000원 주문이다. 잡아 두면 재고 3·포인트 2000·쿠폰 쓰임, 돌려주면 재고 5·포인트 3000·쿠폰 쓸 수 있음이다.
 * 승인이 거절되거나, 카드사에서 만료되거나(승인 요청이 닿지 않음), 결과 모름 결제를 보정·알림 처리가 승인 실패로 닫거나, 전액 취소하면 돌려준다.
 * 결과 모름 동안과 부분 취소는 그대로 둔다. 거절 기록·보정·알림 처리가 같은 실패를 겹쳐 닫아도 한 번만 돌려준다.
 * PG 읽기 시간 제한은 1초, 늦은 카드사는 1.5초 뒤에 답한다. 보정은 승인을 요청한 지 보정 대기 시간(10초)이 지난 때로 한 번 돌린다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class ReleaseContract extends OrderPaymentContract {

    static final Duration LATE = Duration.ofMillis(1500);
    static final Duration WAIT_AFTER_REQUEST = Duration.ofSeconds(10);
    static final BigDecimal POINTS = new BigDecimal("3000");
    static final BigDecimal PAYABLE = new BigDecimal("17000");
    static final int STOCK = 5;
    static final int HELD_STOCK = 3;

    @Autowired
    PaymentReconciler paymentReconciler;

    @Autowired
    PaymentWebhookService paymentWebhookService;

    @Autowired
    PaymentWebhookProcessor paymentWebhookProcessor;

    @Autowired
    PaymentCancelService paymentCancelService;

    Long productId;
    String memberId;
    Long couponId;

    @BeforeEach
    void stockShop() {
        productId = shop.product(PRICE, STOCK);
        memberId = shop.member(POINTS);
        couponId = shop.coupon(memberId, new BigDecimal("2000"));
    }

    @Test
    @DisplayName("승인이 거절되면 잡아 둔 재고·포인트·쿠폰이 처음으로 돌아온다")
    void declinedOrderReturnsHoldings() {
        mockPg.decline();

        orderAndConfirm(orderOf());

        assertReturned();
    }

    @Test
    @DisplayName("승인 요청이 닿지 않아 카드사에서 만료된 결제를 보정이 승인 실패로 닫으면 잡아 둔 것이 돌아온다")
    void expiredOrderClosedByReconciliationReturnsHoldings() {
        String orderId = lostOrder();
        mockPg.expireAtCardCompany(orderId, keyOf(orderId), PAYABLE);

        reconcile();

        assertReturned();
    }

    @Test
    @DisplayName("승인 요청이 닿지 않아 카드사에서 만료된 결제를 알림 처리가 승인 실패로 닫으면 잡아 둔 것이 돌아온다")
    void expiredOrderClosedByWebhookReturnsHoldings() {
        String orderId = lostOrder();
        mockPg.expireAtCardCompany(orderId, keyOf(orderId), PAYABLE);

        receiveWebhook(orderId, "EXPIRED");
        paymentWebhookProcessor.process();

        assertReturned();
    }

    @Test
    @DisplayName("승인 결과를 모르는 동안은 잡아 둔 재고·포인트·쿠폰을 그대로 둔다")
    void unknownOrderKeepsHoldings() {
        mockPg.respondAfter(LATE);

        orderAndConfirm(orderOf());

        assertHeld();
    }

    @Test
    @DisplayName("결과를 모르던 결제를 보정이 승인으로 닫으면 잡아 둔 재고·포인트·쿠폰은 그대로다")
    void unknownOrderApprovedByReconciliationKeepsHoldings() {
        mockPg.respondAfter(LATE);
        PreparePaymentCommand command = orderOf();
        orderAndConfirm(command);
        awaitCardCompanyDone(command.orderId());

        reconcile();

        assertHeld();
    }

    @Test
    @DisplayName("결과를 모르던 결제를 보정이 승인 실패로 닫으면 잡아 둔 것이 돌아온다")
    void unknownOrderDeclinedByReconciliationReturnsHoldings() {
        mockPg.declineAfter(LATE);
        PreparePaymentCommand command = orderOf();
        orderAndConfirm(command);
        awaitCardCompanyDone(command.orderId());

        reconcile();

        assertReturned();
    }

    @Test
    @DisplayName("승인 거절의 기록·보정·알림 처리가 겹쳐도 포인트는 한 번만 돌아온다")
    void overlappingClosersReturnPointsOnce() {
        closeTogether();

        assertThat(shop.pointsOf(memberId)).isEqualByComparingTo(POINTS);
    }

    @Test
    @DisplayName("승인 거절의 기록·보정·알림 처리가 겹쳐도 재고는 한 번만 돌아온다")
    void overlappingClosersReturnStockOnce() {
        closeTogether();

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("승인된 결제를 전액 취소하면 잡아 둔 재고·포인트·쿠폰이 돌아온다")
    void fullCancelReturnsHoldings() {
        PaymentResult approved = orderAndConfirm(orderOf()).result();

        paymentCancelService.cancel(cancelOf(approved.orderId(), null));

        assertReturned();
    }

    @Test
    @DisplayName("승인된 결제를 부분 취소하면 재고·포인트·쿠폰은 그대로다")
    void partialCancelKeepsHoldings() {
        PaymentResult approved = orderAndConfirm(orderOf()).result();

        paymentCancelService.cancel(cancelOf(approved.orderId(), new BigDecimal("5000")));

        assertHeld();
    }

    // 승인이 거절된 결제를 거절 답 기록·보정·알림 처리가 함께 닫는다. 알림 처리가 승인 실패를 남기다 1초 붙잡힌 동안
    // 보정을 시작하고 붙잡아 둔 거절 답을 보낸다
    void closeTogether() {
        mockPg.decline();
        mockPg.holdConfirmAnswers();
        PreparePaymentCommand command = orderOf();
        CompletableFuture<Attempt<PaymentResult>> confirming = Attempt.start(() -> orderAndConfirm(command).result());
        try {
            await().atMost(Duration.ofSeconds(5)).until(() -> "ABORTED".equals(mockPg.paymentState(command.orderId())));
            receiveWebhook(command.orderId(), "ABORTED");
            slowWrites.holdPaymentChangeTo(PaymentStatus.APPROVAL_FAILED);
            CompletableFuture<Attempt<Boolean>> processing = Attempt.start(this::processWebhooks);
            slowWrites.awaitHeld();
            CompletableFuture<Attempt<Boolean>> reconciling = Attempt.start(this::reconcileNow);
            mockPg.releaseConfirmAnswers();
            processing.join();
            reconciling.join();
        } finally {
            mockPg.releaseConfirmAnswers();
            confirming.join();
        }
    }

    // 승인 요청이 카드사에 닿지 않고 답도 오지 않은 결제(결과 모름)
    String lostOrder() {
        mockPg.loseAfter(LATE);
        PreparePaymentCommand command = orderOf();
        orderAndConfirm(command);
        mockPg.reset();
        return command.orderId();
    }

    PreparePaymentCommand orderOf() {
        return order(memberId, productId, 2, couponId, new BigDecimal("1000"), PAYABLE);
    }

    void reconcile() {
        paymentReconciler.reconcile(Instant.now().plus(WAIT_AFTER_REQUEST).plusSeconds(1));
    }

    boolean reconcileNow() {
        reconcile();
        return true;
    }

    boolean processWebhooks() {
        paymentWebhookProcessor.process();
        return true;
    }

    void receiveWebhook(String orderId, String status) {
        paymentWebhookService.receive(new ReceiveWebhookCommand("PAYMENT_STATUS_CHANGED", orderId, status,
                "2026-11-18T10:00:00.000000"));
    }

    // 늦은 카드사가 이 주문을 승인하거나 거절할 때까지 기다린다
    void awaitCardCompanyDone(String orderId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> Set.of("DONE", "ABORTED").contains(mockPg.paymentState(orderId)));
    }

    static CancelPaymentCommand cancelOf(String orderId, BigDecimal amount) {
        return new CancelPaymentCommand(orderId, "cancel-" + orderId, amount, "Customer changed their mind");
    }

    // 재고·포인트·쿠폰이 처음 값(재고 5, 포인트 3000, 쿠폰 쓸 수 있음)이다
    void assertReturned() {
        assertThat(holdingsNow()).containsExactly(STOCK, "3000", "ISSUED");
    }

    // 재고·포인트·쿠폰을 잡아 둔 채다(재고 3, 포인트 2000, 쿠폰 쓰임)
    void assertHeld() {
        assertThat(holdingsNow()).containsExactly(HELD_STOCK, "2000", "USED");
    }

    List<Object> holdingsNow() {
        return List.of(shop.stockOf(productId), shop.pointsOf(memberId).stripTrailingZeros().toPlainString(),
                shop.couponStatusOf(couponId));
    }
}
