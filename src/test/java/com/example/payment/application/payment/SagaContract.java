package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.Attempt;
import com.example.payment.InventoryServerConfiguration;
import com.example.payment.MockPgConfiguration;
import com.example.payment.OtherPaymentServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 10장: 재고를 재고 서비스로 나눈 뒤에도 결제와 재고가 함께 맞게 끝나는가. 결제 하나가 재고 서비스의 재고 잡기와 결제 서버의 승인·취소에
 * 걸쳐 있다 — 한 트랜잭션으로 묶을 수 없어, 한쪽이 끝난 뒤 다른 쪽이 실패하거나 답이 늦거나 순서가 바뀌어도 결국 같은 결과에 닿아야 한다.
 * 지켜야 할 것: 재고가 모자라면 카드사에 승인을 요청하지 않고 결제는 실패로 끝나며 쓴 쿠폰·포인트를 돌려준다. 승인된 결제의 재고는 확정되고,
 * 승인 없이 끝나거나 전액 취소된 결제의 재고는 (재고 서비스가 늦거나 멈췄어도) 결국 돌아온다. 결과 모름 동안은 잡아 둔다.
 * 재고 서비스의 답이 늦어 결제가 그만둔 요청이 나중에 도착해도, 같은 해제·확정이 두 번 와도 재고는 한 번만 바뀐다. 서버 두 대에 주문이 몰려도
 * 처음 재고를 넘겨 팔지 않고, 끝난 뒤 남은 재고와 승인이 남은 결제의 수량을 더하면 처음 재고다.
 * 재고는 결제 서버가 남긴 결과를 모두 보내고(9장 릴레이) 재고 서비스가 받은 것을 다 처리한 뒤에 읽는다(Shop) — "결국"의 끝이다.
 * 재고 서비스 시간 제한은 0.5초, 늦은 재고 서비스·카드사는 1.5초 뒤에 답한다. PG 읽기 시간 제한은 1초, 보정 대기 시간은 10초다.
 * 겹침·실패 시점: 재고 서비스의 답·요청은 중계(InventoryLink)로 붙잡고, 재고 서비스가 멈춘 것은 중계를 끊고 재고 명령 읽기를 멈춰 만든다.
 * 같은 명령이 두 번 가는 것은 보낸 결과를 보내지 않은 것으로 되돌려 다시 보내 만든다(9장 적어도 한 번).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s",
                "payment.inventory.read-timeout=500ms"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InventoryServerConfiguration.class})
abstract class SagaContract extends OrderPaymentContract {

    static final Duration LATE = Duration.ofMillis(1500);
    static final Duration WAIT_AFTER_REQUEST = Duration.ofSeconds(10);
    static final BigDecimal POINTS = new BigDecimal("3000");
    static final int STOCK = 5;
    static final int QUANTITY = 2;
    static final int BURST_STOCK = 100;
    static final int BURST_ORDERS = 300;
    static final int BURST_CANCELS = 10;

    @Autowired
    PaymentReconciler paymentReconciler;

    @Autowired
    PaymentCancelService paymentCancelService;

    @Autowired
    MySQLContainer mysqlContainer;

    Long productId;

    @BeforeEach
    void stockShop() {
        productId = shop.product(PRICE, STOCK);
    }

    @Test
    @DisplayName("재고 서비스가 재고가 모자라다고 답한 주문은 카드사에 승인을 요청하지 않는다")
    void refusedOrderIsNotSentToCardCompany() {
        PreparePaymentCommand command = order(productId, STOCK + 1);

        orderAndConfirm(command);

        assertThat(mockPg.confirmRequestCount(command.orderId())).isZero();
    }

    @Test
    @DisplayName("재고 서비스가 재고가 모자라다고 답한 결제는 승인 없이 실패로 끝난다")
    void refusedOrderFails() {
        PreparePaymentCommand command = order(productId, STOCK + 1);

        orderAndConfirm(command);

        assertThat(paymentQueryService.find(command.orderId()).status()).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    @Test
    @DisplayName("재고가 모자라 받지 않은 주문은 쓴 쿠폰·포인트를 돌려준다")
    void refusedOrderReturnsCouponAndPoints() {
        String memberId = shop.member(POINTS);
        Long couponId = shop.coupon(memberId, new BigDecimal("2000"));

        orderAndConfirm(order(memberId, productId, STOCK + 1, couponId, new BigDecimal("1000"), new BigDecimal("57000")));

        assertThat(List.of(shop.pointsOf(memberId).stripTrailingZeros().toPlainString(), shop.couponStatusOf(couponId)))
                .containsExactly("3000", "ISSUED");
    }

    @Test
    @DisplayName("재고가 모자라 받지 않은 주문은 재고를 그대로 둔다")
    void refusedOrderKeepsStock() {
        orderAndConfirm(order(productId, STOCK + 1));

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("승인된 주문의 재고 예약은 확정된다")
    void approvedOrderConfirmsReservation() {
        orderAndConfirm(order(productId, QUANTITY));

        assertThat(shop.confirmedQuantityOf(productId)).isEqualTo(QUANTITY);
    }

    @Test
    @DisplayName("카드사가 승인을 거절한 주문의 재고는 처음으로 돌아온다")
    void declinedOrderReturnsStock() {
        mockPg.decline();

        orderAndConfirm(order(productId, QUANTITY));

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("승인 요청이 닿지 않아 카드사에서 만료된 결제를 보정이 닫으면 재고는 처음으로 돌아온다")
    void expiredOrderReturnsStock() {
        String orderId = lostOrder();
        mockPg.expireAtCardCompany(orderId, keyOf(orderId), PRICE.multiply(BigDecimal.valueOf(QUANTITY)));

        reconcile();

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("승인된 결제를 전액 취소하면 재고는 처음으로 돌아온다")
    void fullCancelReturnsStock() {
        PaymentResult approved = orderAndConfirm(order(productId, QUANTITY)).result();

        paymentCancelService.cancel(cancelOf(approved.orderId()));

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("승인 결과를 모르는 동안은 재고 예약을 그대로 둔다")
    void unknownOrderKeepsStock() {
        mockPg.respondAfter(LATE);

        orderAndConfirm(order(productId, QUANTITY));

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK - QUANTITY);
    }

    @Test
    @DisplayName("결과를 모르던 결제를 보정이 승인으로 닫으면 재고 예약은 확정된다")
    void unknownOrderApprovedByReconciliationConfirmsReservation() {
        mockPg.respondAfter(LATE);
        PreparePaymentCommand command = order(productId, QUANTITY);
        orderAndConfirm(command);
        awaitCardCompanyDone(command.orderId());

        reconcile();

        assertThat(shop.confirmedQuantityOf(productId)).isEqualTo(QUANTITY);
    }

    @Test
    @DisplayName("결과를 모르던 결제를 보정이 승인 실패로 닫으면 재고는 처음으로 돌아온다")
    void unknownOrderDeclinedByReconciliationReturnsStock() {
        mockPg.declineAfter(LATE);
        PreparePaymentCommand command = order(productId, QUANTITY);
        orderAndConfirm(command);
        awaitCardCompanyDone(command.orderId());

        reconcile();

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("재고 서비스의 답이 시간 제한보다 늦은 주문은 카드사에 승인을 요청하지 않는다")
    void lateInventoryAnswerIsNotSentToCardCompany() {
        inventory.link().slowAnswers(LATE);
        PreparePaymentCommand command = order(productId, QUANTITY);

        orderAndConfirm(command);

        assertThat(mockPg.confirmRequestCount(command.orderId())).isZero();
    }

    @Test
    @DisplayName("재고 서비스의 답이 시간 제한보다 늦어도 그 주문이 잡은 재고는 처음으로 돌아온다")
    void lateInventoryAnswerReturnsStock() {
        inventory.link().slowAnswers(LATE);
        orderAndConfirm(order(productId, QUANTITY));

        inventory.link().restore();

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("해제가 예약보다 먼저 도착해도 재고는 처음보다 늘지 않는다")
    void releaseBeforeReservationDoesNotAddStock() {
        inventory.link().holdRequests();

        orderAndConfirm(order(productId, QUANTITY));

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("해제보다 늦게 도착한 예약은 재고를 줄이지 않는다")
    void reservationAfterReleaseDoesNotTakeStock() {
        inventory.link().holdRequests();
        orderAndConfirm(order(productId, QUANTITY));
        shop.settle();

        inventory.link().releaseRequests();
        inventory.link().awaitDelivered();

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("같은 해제가 두 번 와도 재고는 한 번만 돌아온다")
    void repeatedReleaseReturnsStockOnce() {
        mockPg.decline();
        PreparePaymentCommand command = order(productId, QUANTITY);
        orderAndConfirm(command);

        sendAgain(command.orderId());

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("같은 확정이 두 번 와도 재고는 승인된 수량만큼만 줄어 있다")
    void repeatedConfirmTakesStockOnce() {
        PreparePaymentCommand command = order(productId, QUANTITY);
        orderAndConfirm(command);

        sendAgain(command.orderId());

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK - QUANTITY);
    }

    @Test
    @DisplayName("재고 서비스가 멈춘 동안 승인 없이 끝난 주문의 재고는 재고 서비스가 돌아오면 처음으로 돌아온다")
    void orderClosedWhileInventoryDownReturnsStockAfterRecovery() {
        PreparePaymentCommand command = order(productId, QUANTITY);
        mockPg.decline();
        mockPg.holdConfirmAnswers();
        CompletableFuture<Attempt<PaymentResult>> confirming = Attempt.start(() -> orderAndConfirm(command).result());
        try {
            await().atMost(Duration.ofSeconds(5)).until(() -> "ABORTED".equals(mockPg.paymentState(command.orderId())));
            inventory.down();
        } finally {
            mockPg.releaseConfirmAnswers();
            confirming.join();
        }
        sendWhileDown();

        inventory.up();

        assertThat(shop.stockOf(productId)).isEqualTo(STOCK);
    }

    @Test
    @DisplayName("재고 100개에 두 서버로 주문 300건이 한꺼번에 와도 승인된 주문은 100건을 넘지 않는다")
    void burstOnTwoServersDoesNotOversell() {
        Long burstProduct = shop.product(PRICE, BURST_STOCK);

        List<Attempt<PaymentResult>> answers = burstOnTwoServers(burstProduct);

        assertThat(StockContract.approvedCountOf(answers)).isLessThanOrEqualTo(BURST_STOCK);
    }

    @Test
    @DisplayName("재고 100개에 두 서버로 주문 300건이 몰린 뒤 일부를 취소해도 남은 재고와 승인이 남은 결제의 수량을 더하면 처음 재고다")
    void burstOnTwoServersKeepsStockWithApprovals() {
        Long burstProduct = shop.product(PRICE, BURST_STOCK);
        List<Attempt<PaymentResult>> answers = burstOnTwoServers(burstProduct);

        cancelSomeApproved(answers);

        assertThat(shop.stockOf(burstProduct) + approvedQuantityOf(burstProduct)).isEqualTo(BURST_STOCK);
    }

    // 승인 요청이 카드사에 닿지 않고 답도 오지 않은 결제(결과 모름)
    String lostOrder() {
        mockPg.loseAfter(LATE);
        PreparePaymentCommand command = order(productId, QUANTITY);
        orderAndConfirm(command);
        mockPg.reset();
        return command.orderId();
    }

    void reconcile() {
        paymentReconciler.reconcile(Instant.now().plus(WAIT_AFTER_REQUEST).plusSeconds(1));
    }

    // 늦은 카드사가 이 주문을 승인하거나 거절할 때까지 기다린다
    void awaitCardCompanyDone(String orderId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> Set.of("DONE", "ABORTED").contains(mockPg.paymentState(orderId)));
    }

    static CancelPaymentCommand cancelOf(String orderId) {
        return new CancelPaymentCommand(orderId, "cancel-" + orderId, null, "Customer changed their mind");
    }

    // 이 주문에 대해 보낸 결과를 모두 보낸 뒤, 보내지 않은 것으로 되돌린다 — 다음에 재고를 읽을 때 같은 결과가 한 번 더 간다
    void sendAgain(String orderId) {
        shop.settle();
        jdbcTemplate.update("update payment_outbox set sent_at = null where order_id = ?", orderId);
    }

    // 재고 서비스가 멈춘 동안 남은 결과를 보낸다(브로커는 받는다 — 재고 서비스가 읽지 않을 뿐이다)
    void sendWhileDown() {
        for (int round = 0; round < 100 && hasUnsent(); round++) {
            paymentOutboxRelay.relay();
        }
    }

    private boolean hasUnsent() {
        Integer unsent = jdbcTemplate.queryForObject("select count(*) from payment_outbox where sent_at is null",
                Integer.class);
        return Objects.requireNonNullElse(unsent, 0) > 0;
    }

    // 결제 서버를 한 대 더 띄우고, 준비해 둔 주문 300건의 승인을 두 서버에 번갈아 스레드 300개에서 한꺼번에 보낸다
    List<Attempt<PaymentResult>> burstOnTwoServers(Long burstProduct) {
        List<PaymentResult> prepared = IntStream.range(0, BURST_ORDERS)
                .mapToObj(index -> prepare(order(burstProduct, 1)))
                .toList();
        ConfigurableApplicationContext other = OtherPaymentServer.start(mysqlContainer, mockPg, List.of(
                "payment.inventory.base-url=" + inventory.url(),
                "payment.inventory.commands-topic=" + inventory.commandsTopic(),
                "payment.inventory.read-timeout=500ms"));
        try {
            PaymentConfirmService otherConfirm = other.getBean(PaymentConfirmService.class);
            return confirmAtOnce(prepared, otherConfirm);
        } finally {
            other.close();
        }
    }

    private List<Attempt<PaymentResult>> confirmAtOnce(List<PaymentResult> prepared, PaymentConfirmService otherConfirm) {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(prepared.size());
        try {
            List<CompletableFuture<Attempt<PaymentResult>>> answers = IntStream.range(0, prepared.size())
                    .mapToObj(index -> Attempt.start(() -> {
                        awaitStart(start);
                        return confirmOn(index, prepared.get(index), otherConfirm);
                    }, executor))
                    .toList();
            start.countDown();
            return answers.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdown();
        }
    }

    private PaymentResult confirmOn(int index, PaymentResult payment, PaymentConfirmService otherConfirm) {
        ConfirmPaymentCommand command = new ConfirmPaymentCommand(payment.orderId(), keyOf(payment.orderId()), payment.amount());
        if (index % 2 == 0) {
            return confirm(command);
        }
        return otherConfirm.confirm(command);
    }

    // 승인된 결제 가운데 앞의 몇 건을 전액 취소한다
    void cancelSomeApproved(List<Attempt<PaymentResult>> answers) {
        answers.stream()
                .filter(Attempt::isAnswered)
                .map(Attempt::result)
                .filter(result -> result.status() == PaymentStatus.APPROVED)
                .limit(BURST_CANCELS)
                .forEach(result -> paymentCancelService.cancel(cancelOf(result.orderId())));
    }

    // 결제 서버 기록에서 승인이 남은 결제(승인, 매입)가 담은 이 상품의 수량
    int approvedQuantityOf(Long product) {
        Integer quantity = jdbcTemplate.queryForObject("select coalesce(sum(i.quantity), 0) from payment_item i "
                + "join payment p on p.id = i.payment_id where p.status in ('APPROVED', 'CAPTURED') and i.product_id = ?",
                Integer.class, product);
        return Objects.requireNonNullElse(quantity, 0);
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
