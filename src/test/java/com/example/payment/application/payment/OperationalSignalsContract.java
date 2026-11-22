package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.DeadLetterTopic;
import com.example.payment.InventoryServer;
import com.example.payment.InventoryServerConfiguration;
import com.example.payment.MessageFeed;
import com.example.payment.MockPgConfiguration;
import com.example.payment.OrderServer;
import com.example.payment.OrderServerConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 12장: 운영 신호와 정리의 규칙. 실패 모음(11장 DLT)에 메시지가 쌓여도, 릴레이(9장)가 밀려도 바깥에서는 알 수 없고, 보낸 결제 결과와
 * 받은 기록(주문 서비스·재고 서비스)은 지워지지 않고 쌓인다.
 * 지켜야 할 것: 실패 모음에 남은 수, 보내지 않은 결제 결과 수와 가장 오래 기다린 결과의 나이, 운영자가 확인할 대사 어긋남 수가 운영 지표에
 * 실제 값대로 보인다. 보낸 결제 결과와 받은 기록은 보존 기간이 지난 것만 지우고, 보내지 않은 결과는 지우지 않는다. 받은 기록을 지운 뒤에도
 * 같은 결과가 다시 오면 주문은 다시 바뀌지 않는다(11장 중복 가리기).
 * 지표·정리 방법이 구현마다 다르므로 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * 보존 기간(운영 값): 보낸 결제 결과 7일, 받은 기록 14일. 시험은 기록의 때를 그보다 앞이나 뒤로 고쳐 둔다.
 * 실패 모음은 받는 쪽 기록을 트리거로 실패시키고, 다시 시도를 계약에서 0.1초 한 번으로 줄여 만든다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "test.order.properties=order.retry.initial-interval=100ms;order.retry.multiplier=1;order.retry.max-interval=100ms;"
                        + "order.retry.max-retries=1",
                "test.inventory.properties=inventory.retry.initial-interval=100ms;inventory.retry.multiplier=1;"
                        + "inventory.retry.max-interval=100ms;inventory.retry.max-retries=1"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, OrderServerConfiguration.class,
        InventoryServerConfiguration.class})
abstract class OperationalSignalsContract {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final Duration ARRIVAL = Duration.ofSeconds(30);
    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final AtomicLong NUMBERS = new AtomicLong(1_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000L) * 1000);

    @Autowired
    OrderServer orderServer;

    @Autowired
    InventoryServer inventory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    MessageFeed feed;

    /** 결제 서버의 운영 지표. 지표가 없는 구현은 비어 있다 */
    abstract Optional<Double> paymentMeter(String name);

    /** 주문 서비스의 운영 지표 */
    abstract Optional<Double> orderMeter(String name);

    /** 재고 서비스의 운영 지표 */
    abstract Optional<Double> inventoryMeter(String name);

    /** 결제 서버의 정리(보낸 결제 결과)를 지금(now) 한 번 돌린다. 정리하지 않는 구현은 할 것이 없다 */
    abstract void cleanUpPayments(Instant now);

    /** 주문 서비스의 정리(받은 결제 결과)를 한 번 돌린다 */
    abstract void cleanUpOrders(Instant now);

    /** 재고 서비스의 정리(처리한 재고 명령)를 한 번 돌린다 */
    abstract void cleanUpInventory(Instant now);

    /** 이날의 대사를 한 번 돌린다. 대사가 없는 구현은 할 것이 없다 */
    abstract void reconcile(LocalDate day);

    @BeforeEach
    void openFeed() {
        feed = MessageFeed.open();
    }

    @AfterEach
    void closeFeed() {
        orderServer.restoreOrderWrites();
        inventory.restoreReservationChange();
        feed.close();
    }

    @Test
    @DisplayName("주문 서비스 실패 모음에 결제 결과가 하나 더 쌓이면 운영 지표도 하나 는다")
    void orderDeadLetterIsCounted() {
        Optional<Double> before = orderMeter("order.dead-letter.pending");

        failOrderEvent();

        assertThat(before).isPresent();
        assertThat(orderMeter("order.dead-letter.pending")).contains(before.orElseThrow() + 1);
    }

    @Test
    @DisplayName("실패 모음에서 다시 넣은 결제 결과는 운영 지표에서 빠진다")
    void replayedDeadLetterIsNotCounted() {
        String orderId = failOrderEvent();
        orderServer.restoreOrderWrites();

        orderServer.replayDeadLetters();
        await().atMost(ARRIVAL).until(() -> "PAID".equals(orderServer.statusOf(orderId)));

        assertThat(orderMeter("order.dead-letter.pending")).contains(0.0);
    }

    @Test
    @DisplayName("재고 서비스 실패 모음에 재고 명령이 하나 더 쌓이면 운영 지표도 하나 는다")
    void inventoryDeadLetterIsCounted() {
        Optional<Double> before = inventoryMeter("inventory.dead-letter.pending");
        long reservationId = reserved();
        inventory.failReservationChange(reservationId, "CONFIRMED");
        int letters = DeadLetterTopic.records(DeadLetterTopic.of(inventory.commandsTopic())).size();

        sendCommand(reservationId, "CONFIRM");
        await().atMost(ARRIVAL).until(() -> DeadLetterTopic.records(DeadLetterTopic.of(inventory.commandsTopic())).size() > letters);

        assertThat(before).isPresent();
        assertThat(inventoryMeter("inventory.dead-letter.pending")).contains(before.orElseThrow() + 1);
    }

    @Test
    @DisplayName("보내지 않은 결제 결과 수가 운영 지표에 보인다")
    void unsentResultsAreCounted() {
        approved();

        assertThat(paymentMeter("payment.outbox.unsent")).contains((double) count("select count(*) from payment_outbox where sent_at is null"));
    }

    @Test
    @DisplayName("보내지 않은 결제 결과 가운데 가장 오래 기다린 결과의 나이가 운영 지표에 초로 보인다")
    void oldestUnsentAgeIsShown() {
        String orderId = approved();
        // 앞 시험이 남긴 보내지 않은 결과는 보낸 것으로 둔다(릴레이는 꺼 두었다) — 이 결과가 가장 오래 기다린 결과가 된다
        jdbcTemplate.update("update payment_outbox set sent_at = ? where sent_at is null and order_id <> ?", Timestamp.from(Instant.now()),
                orderId);
        jdbcTemplate.update("update payment_outbox set occurred_at = ? where order_id = ?",
                utc(Instant.now().minus(1, ChronoUnit.DAYS)), orderId);

        assertThat(paymentMeter("payment.outbox.oldest-unsent-age")).hasValueSatisfying(age ->
                assertThat(age).isBetween(86_400.0, 86_460.0));
    }

    @Test
    @DisplayName("운영자가 확인할 대사 어긋남 수가 운영 지표에 보인다")
    void openMismatchesAreCounted() {
        String orderId = "order-" + UUID.randomUUID();
        jdbcTemplate.update("insert into payment (order_id, amount, status, payment_key, authenticated_at, point_amount) "
                + "values (?, ?, 'APPROVED', ?, now(6), 0)", orderId, PREPARED, "key-" + orderId);
        reconcile(LocalDate.now(KOREA));

        Optional<Double> meter = paymentMeter("payment.reconciliation.open-mismatches");

        assertThat(meter).isPresent();
        assertThat(meter).contains((double) count("select count(*) from reconciliation_result where resolution = 'NEEDS_REVIEW'"));
    }

    @Test
    @DisplayName("보존 기간이 지난 보낸 결제 결과는 정리에서 지워진다")
    void oldSentResultIsDeleted() {
        String orderId = approved();
        markSent(orderId, Instant.now().minus(8, ChronoUnit.DAYS));

        cleanUpPayments(Instant.now());

        assertThat(outboxCountOf(orderId)).isZero();
    }

    @Test
    @DisplayName("보존 기간이 지나지 않은 보낸 결제 결과는 정리에서 남는다")
    void recentSentResultIsKept() {
        String orderId = approved();
        markSent(orderId, Instant.now().minus(6, ChronoUnit.DAYS));

        cleanUpPayments(Instant.now());

        assertThat(outboxCountOf(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("보내지 않은 결제 결과는 오래돼도 정리에서 지우지 않는다")
    void unsentResultIsKept() {
        String orderId = approved();
        jdbcTemplate.update("update payment_outbox set occurred_at = ? where order_id = ?",
                utc(Instant.now().minus(30, ChronoUnit.DAYS)), orderId);

        cleanUpPayments(Instant.now());

        assertThat(outboxCountOf(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("보존 기간이 지난 주문 서비스의 받은 결제 결과는 정리에서 지워진다")
    void oldReceivedEventIsDeleted() {
        String orderId = paidOrder(NUMBERS.incrementAndGet());
        ageReceived(orderId, 15);

        cleanUpOrders(Instant.now());

        assertThat(orderServer.receivedCount(orderId)).isZero();
    }

    @Test
    @DisplayName("보존 기간이 지나지 않은 주문 서비스의 받은 결제 결과는 정리에서 남는다")
    void recentReceivedEventIsKept() {
        String orderId = paidOrder(NUMBERS.incrementAndGet());
        ageReceived(orderId, 13);

        cleanUpOrders(Instant.now());

        assertThat(orderServer.receivedCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("받은 결제 결과가 정리된 뒤 같은 결과가 다시 와도 주문은 다시 바뀌지 않는다")
    void resentResultAfterCleanupDoesNotChangeOrder() {
        long eventId = NUMBERS.incrementAndGet();
        String orderId = paidOrder(eventId);
        ageReceived(orderId, 15);
        cleanUpOrders(Instant.now());
        Instant updatedAt = orderServer.updatedAtOf(orderId);

        sendEvent(eventId, orderId, "APPROVED");
        orderServer.awaitCaughtUp(ARRIVAL);

        assertThat(orderServer.updatedAtOf(orderId)).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("보존 기간이 지난 재고 서비스의 처리한 재고 명령은 정리에서 지워진다")
    void oldProcessedCommandIsDeleted() {
        long reservationId = reserved();
        sendCommand(reservationId, "CONFIRM");
        await().atMost(ARRIVAL).until(() -> "CONFIRMED".equals(inventory.reservationStatusOf(reservationId)));
        inventory.execute("update processed_stock_command set processed_at = '" + utc(Instant.now().minus(15, ChronoUnit.DAYS))
                + "' where reservation_id = " + reservationId);

        cleanUpInventory(Instant.now());

        assertThat(inventory.rows("select * from processed_stock_command where reservation_id = " + reservationId)).isEmpty();
    }

    // 결제가 승인되고 승인 결과가 보내지 않은 채 남는다(릴레이는 꺼 둔다)
    String approved() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        return orderId;
    }

    // 보낸 때는 릴레이가 JDBC 로 이 서버 시간대 시각을 남긴다(PaymentOutboxRepositoryAdapter.markSent 와 같게)
    void markSent(String orderId, Instant sentAt) {
        jdbcTemplate.update("update payment_outbox set sent_at = ? where order_id = ?", Timestamp.from(sentAt), orderId);
    }

    // 결제 결과를 남긴 때(occurred_at)와 재고 서비스가 명령을 처리한 때는 UTC 시각으로 남는다(JPA, 재고 서비스 JDBC)
    static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    int outboxCountOf(String orderId) {
        return count("select count(*) from payment_outbox where order_id = '" + orderId + "'");
    }

    int count(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return Objects.requireNonNullElse(count, 0);
    }

    // 주문 서비스가 이 번호의 승인 결과를 받아 결제 완료가 된 주문
    String paidOrder(long eventId) {
        String orderId = "order-" + UUID.randomUUID();
        sendEvent(eventId, orderId, "APPROVED");
        await().atMost(ARRIVAL).until(() -> "PAID".equals(orderServer.statusOf(orderId)));
        return orderId;
    }

    // 주문 서비스는 받은 때를 JDBC 로 그 서버 시간대 시각으로 남긴다(시험 JVM 과 같은 시간대)
    void ageReceived(String orderId, int days) {
        orderServer.execute("update received_payment_event set received_at = '"
                + Timestamp.from(Instant.now().minus(days, ChronoUnit.DAYS)) + "' where order_id = '" + orderId + "'");
    }

    // 주문 서비스가 쓰지 못해 다시 시도를 다 쓰고 실패 모음에 남은 승인 결과의 주문
    String failOrderEvent() {
        String orderId = "order-" + UUID.randomUUID();
        orderServer.failOrderWrites(orderId, "APPROVED");
        int letters = DeadLetterTopic.records(DeadLetterTopic.of(orderServer.topic())).size();
        sendEvent(NUMBERS.incrementAndGet(), orderId, "APPROVED");
        await().atMost(ARRIVAL).until(() -> DeadLetterTopic.records(DeadLetterTopic.of(orderServer.topic())).size() > letters);
        return orderId;
    }

    void sendEvent(long eventId, String orderId, String status) {
        long paymentId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
        feed.send(orderServer.topic(), String.valueOf(paymentId), "{\"eventId\":\"" + eventId + "\",\"paymentId\":" + paymentId
                + ",\"orderId\":\"" + orderId + "\",\"status\":\"" + status + "\",\"occurredAt\":\"" + Instant.now() + "\"}");
    }

    long reserved() {
        long reservationId = NUMBERS.incrementAndGet();
        long productId = NUMBERS.incrementAndGet();
        inventory.stock(productId, 10);
        inventory.reserve(reservationId, productId, 1);
        return reservationId;
    }

    void sendCommand(long reservationId, String command) {
        feed.send(inventory.commandsTopic(), String.valueOf(reservationId), "{\"commandId\":\"" + NUMBERS.incrementAndGet()
                + "\",\"reservationId\":" + reservationId + ",\"orderId\":\"order-" + reservationId + "\",\"command\":\"" + command
                + "\"}");
    }
}
