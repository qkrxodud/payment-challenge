package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.DeadLetter;
import com.example.payment.DeadLetterTopic;
import com.example.payment.KafkaBroker;
import com.example.payment.MessageFeed;
import com.example.payment.MockPgConfiguration;
import com.example.payment.OrderServer;
import com.example.payment.OrderServerConfiguration;
import com.example.payment.ReceiverOutage;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.testcontainers.mysql.MySQLContainer;

/**
 * 11장: 결제 이벤트를 받는 주문 서비스(소비자)의 규칙. 결제 서버는 결제 결과를 적어도 한 번 보낸다(9장) — 같은 결과가 다시 올 수 있고,
 * 소비자가 처리한 뒤 읽은 자리를 커밋하기 전에 다시 뜨면 같은 결과를 다시 읽는다. 다시 넣은 결과나 늦게 온 옛 결과가 새 결과 뒤에 올 수도 있고,
 * 처리할 수 없는 결과(모르는 결제 상태, 읽을 수 없는 본문)가 오거나, 주문 서비스 DB 가 잠시 쓰기를 거부할 수도 있다.
 * 지켜야 할 것: 같은 결과는 한 번만 반영하고, 새 결과 뒤에 온 옛 결과는 반영하지 않는다. 처리할 수 없는 결과는 다시 시도하지 않고
 * 실패 모음(<토픽>.DLT)에 원인과 함께 남기며 뒤 결과를 붙잡지 않는다. 일시 장애 동안 온 결과는 잃지 않고 장애가 끝나면 반영하고,
 * 다시 시도를 다 쓴 결과도 실패 모음에 원인과 함께 남긴다. 실패 모음에서 다시 넣은 결과는 같은 규칙(한 번만, 옛 결과는 반영하지 않음)을 따른다.
 * 같은 주문의 결과는 다시 시도하는 동안에도 보낸 순서대로 받는다.
 * 결과는 시험이 주문 서비스가 읽는 토픽에 직접 싣는다(MessageFeed — 키 = 결제 번호, 결제 서버 릴레이와 같다). 이벤트 고유 번호는 결제 서버의
 * payment_outbox 번호와 겹치지 않게 큰 수에서 시작한다. "다 처리했다"는 주문 서비스의 소비자 그룹이 커밋한 자리가 토픽 끝에 닿은 것으로 본다.
 * 받는 쪽 DB 장애는 MySQL 을 읽기 전용으로 돌려 만들고(ReceiverOutage), 한 주문의 기록만 실패하는 장애는 트리거로 만든다.
 * 주문 서비스의 다시 시도 간격은 계약에서 2·4·4·4초로 줄였다(운영 값은 order-service.properties 와 docs/ch11/environment.md).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "test.order.properties=order.retry.initial-interval=2s;order.retry.multiplier=2;order.retry.max-interval=4s;"
                        + "order.retry.max-retries=4"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, OrderServerConfiguration.class})
abstract class PaymentEventConsumerContract {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    // 계약의 첫 다시 시도 간격(2초). 다시 시도하지 않은 결과는 이보다 빨리 뒤 결과에 자리를 내준다
    static final Duration FIRST_RETRY = Duration.ofSeconds(2);
    // 일시 장애 6초: 계약의 다시 시도(2·4·4·4초, 합 14초)보다 짧고, Spring Kafka 기본 처리(10번)가 이 시험 환경에서 다 쓰는 시간(약 4.6초,
    // 출발 구조 실행에서 봄 — 다시 읽을 때마다 약 0.5초)보다 길다
    static final Duration OUTAGE = Duration.ofSeconds(6);
    static final Duration ARRIVAL = Duration.ofSeconds(30);
    private static final AtomicLong EVENT_NUMBERS = new AtomicLong(1_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000L) * 1000);

    @Autowired
    OrderServer orderServer;

    @Autowired
    MySQLContainer mysqlContainer;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentOutboxRelay paymentOutboxRelay;

    MessageFeed feed;

    ReceiverOutage outage;

    @BeforeEach
    void openFeed() {
        feed = MessageFeed.open();
        outage = ReceiverOutage.of(mysqlContainer);
    }

    @AfterEach
    void closeFeed() {
        outage.end();
        orderServer.restoreOrderWrites();
        feed.close();
    }

    // 실패 모음에 지금까지 남은 결과. 운영 구현은 <토픽>.DLT 이고, 다른 곳에 모으는 비교 구현은 바꾼다
    List<DeadLetter> deadLetters() {
        return DeadLetterTopic.read(DeadLetterTopic.of(orderServer.topic()));
    }

    // 운영자가 실패 모음의 결과를 원래 토픽으로 다시 넣는다
    void replayDeadLetters() {
        orderServer.replayDeadLetters();
    }

    @Test
    @DisplayName("결제 서버가 같은 결제 결과를 다시 보내도 주문 서비스는 받은 결과를 한 번만 남긴다")
    void resentResultIsRecordedOnce() {
        String orderId = approvedOrder();
        relayAll();
        orderServer.awaitReceived(orderId, "APPROVED", ARRIVAL);

        jdbcTemplate.update("update payment_outbox set sent_at = null where order_id = ?", orderId);
        relayAll();
        orderServer.awaitCaughtUp(ARRIVAL);

        assertThat(orderServer.receivedCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 결제 결과를 다시 읽어도 주문은 다시 바뀌지 않는다")
    void rereadResultDoesNotChangeOrderAgain() {
        Event approved = Event.approved(newPayment());
        send(approved);
        awaitStatus(approved.orderId(), "PAID");
        Instant firstChange = orderServer.updatedAtOf(approved.orderId());

        send(approved);
        orderServer.awaitCaughtUp(ARRIVAL);

        assertThat(orderServer.updatedAtOf(approved.orderId())).isEqualTo(firstChange);
    }

    @Test
    @DisplayName("취소 뒤에 그보다 먼저 만든 승인이 늦게 와도 주문은 취소 상태로 남는다")
    void lateOlderApprovalDoesNotRevertCanceledOrder() {
        Event approved = Event.approved(newPayment());
        Event canceled = approved.next("APPROVAL_CANCELED", Duration.ofMillis(200));

        send(canceled);
        awaitStatus(approved.orderId(), "CANCELED");
        send(approved);
        orderServer.awaitCaughtUp(ARRIVAL);

        assertThat(orderServer.statusOf(approved.orderId())).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("결제 서버 시계가 서로 달라 새 결과의 만든 시각이 앞선 결과보다 이르게 찍혀도 새 결과가 반영된다")
    void newerResultWithEarlierClockIsApplied() {
        Event approved = Event.approved(newPayment());
        // 7장 서버 두 대: 승인을 남긴 서버보다 시계가 0.3초 늦은 서버가 0.2초 뒤에 취소를 남겼다
        Event canceled = approved.next("APPROVAL_CANCELED", Duration.ofMillis(-100));

        send(approved);
        awaitStatus(approved.orderId(), "PAID");
        send(canceled);
        orderServer.awaitCaughtUp(ARRIVAL);

        assertThat(orderServer.statusOf(approved.orderId())).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("모르는 결제 상태가 실린 결과는 실패 모음에 남는다")
    void unknownStatusGoesToDeadLetters() {
        Event unknown = Event.of(newPayment(), "REFUNDED");

        send(unknown);

        awaitDeadLetter(unknown.body());
    }

    @Test
    @DisplayName("읽을 수 없는 결과는 실패 모음에 남는다")
    void unreadableResultGoesToDeadLetters() {
        String body = "{\"eventId\": \"" + EVENT_NUMBERS.incrementAndGet() + "\", \"orderId\": ";

        feed.send(orderServer.topic(), "broken-" + UUID.randomUUID(), body);

        awaitDeadLetter(body);
    }

    @Test
    @DisplayName("처리할 수 없는 결과가 있어도 같은 자리의 뒤 결과는 첫 다시 시도 간격 안에 반영된다")
    void unprocessableResultDoesNotHoldNextResult() {
        Event unknown = Event.of(newPayment(), "REFUNDED");
        Event next = Event.approved(newPaymentOnSamePartitionAs(unknown.key()));

        long sentAt = System.nanoTime();
        send(unknown);
        send(next);
        awaitStatus(next.orderId(), "PAID");

        assertThat(Duration.ofNanos(System.nanoTime() - sentAt)).isLessThan(FIRST_RETRY);
    }

    @Test
    @DisplayName("주문 서비스의 저장소가 잠시 쓰기를 거부한 동안 온 결과도 저장소가 돌아오면 반영된다")
    void resultDuringOutageIsAppliedAfterRecovery() {
        Event approved = Event.approved(newPayment());
        outage.begin();

        send(approved);
        sleep(OUTAGE);
        outage.end();

        awaitStatus(approved.orderId(), "PAID");
    }

    @Test
    @DisplayName("다시 시도를 다 쓴 결과는 실패 모음에 그 원인과 함께 남는다")
    void exhaustedResultGoesToDeadLettersWithCause() {
        Event approved = Event.approved(newPayment());
        outage.begin();

        send(approved);
        DeadLetter letter = awaitDeadLetter(approved.body());

        assertThat(letter.cause()).isNotBlank();
    }

    @Test
    @DisplayName("실패 모음에서 다시 넣은 결과는 아직 반영되지 않았으면 반영된다")
    void replayedResultIsAppliedWhenNotYetApplied() {
        Event approved = Event.approved(newPayment());
        outage.begin();
        send(approved);
        awaitDeadLetter(approved.body());
        outage.end();

        replayDeadLetters();

        awaitStatus(approved.orderId(), "PAID");
    }

    @Test
    @DisplayName("실패 모음에서 다시 넣은 결과는 그사이 더 새 결과가 반영됐으면 무시된다")
    void replayedResultIsIgnoredAfterNewerResult() {
        Event approved = Event.approved(newPayment());
        outage.begin();
        send(approved);
        awaitDeadLetter(approved.body());
        outage.end();
        send(approved.next("APPROVAL_CANCELED", Duration.ofMillis(200)));
        awaitStatus(approved.orderId(), "CANCELED");

        replayDeadLetters();
        orderServer.awaitCaughtUp(ARRIVAL);

        assertThat(orderServer.statusOf(approved.orderId())).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("같은 주문의 결과는 앞 결과를 다시 시도하는 동안에도 보낸 순서대로 받는다")
    void resultsOfOneOrderAreReceivedInOrderWhileRetrying() {
        Event approved = Event.approved(newPayment());
        orderServer.failOrderWrites(approved.orderId(), "APPROVED");

        send(approved);
        send(approved.next("APPROVAL_CANCELED", Duration.ofMillis(200)));
        sleep(OUTAGE);
        orderServer.restoreOrderWrites();
        await().atMost(ARRIVAL).until(() -> orderServer.receivedCount(approved.orderId()) >= 2);

        assertThat(orderServer.receivedStatuses(approved.orderId())).containsExactly("APPROVED", "APPROVAL_CANCELED");
    }

    String approvedOrder() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        return orderId;
    }

    void relayAll() {
        for (int round = 0; round < 100 && hasUnsent(); round++) {
            paymentOutboxRelay.relay();
        }
    }

    private boolean hasUnsent() {
        Integer unsent = jdbcTemplate.queryForObject("select count(*) from payment_outbox where sent_at is null", Integer.class);
        return Objects.requireNonNullElse(unsent, 0) > 0;
    }

    void send(Event event) {
        feed.send(orderServer.topic(), event.key(), event.body());
    }

    void awaitStatus(String orderId, String status) {
        await().atMost(ARRIVAL).pollInterval(Duration.ofMillis(20)).until(() -> status.equals(orderServer.statusOf(orderId)));
    }

    DeadLetter awaitDeadLetter(String body) {
        return await().atMost(ARRIVAL).pollInterval(Duration.ofMillis(200))
                .until(() -> deadLetters().stream().filter(letter -> body.equals(letter.value())).findFirst(),
                        Optional::isPresent)
                .orElseThrow();
    }

    static Payment newPayment() {
        return Payment.of(ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L));
    }

    // 이 키와 같은 파티션에 실릴 다른 결제
    static Payment newPaymentOnSamePartitionAs(String key) {
        int partition = MessageFeed.partitionOf(key, KafkaBroker.PARTITIONS);
        Payment candidate = newPayment();
        while (MessageFeed.partitionOf(candidate.key(), KafkaBroker.PARTITIONS) != partition) {
            candidate = newPayment();
        }
        return candidate;
    }

    static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** 시험이 싣는 결제 하나(결제 번호 = 메시지 키, 주문번호) */
    static class Payment {

        private final long paymentId;
        private final String orderId;

        private Payment(long paymentId, String orderId) {
            this.paymentId = paymentId;
            this.orderId = orderId;
        }

        static Payment of(long paymentId) {
            return new Payment(paymentId, "order-" + UUID.randomUUID());
        }

        String key() {
            return String.valueOf(paymentId);
        }
    }

    /** 시험이 싣는 결제 결과 한 건(결제 서버가 보내는 본문과 같은 모양) */
    static class Event {

        private final Payment payment;
        private final long eventId;
        private final String status;
        private final Instant occurredAt;

        private Event(Payment payment, long eventId, String status, Instant occurredAt) {
            this.payment = payment;
            this.eventId = eventId;
            this.status = status;
            this.occurredAt = occurredAt;
        }

        static Event of(Payment payment, String status) {
            return new Event(payment, EVENT_NUMBERS.incrementAndGet(), status, Instant.now());
        }

        static Event approved(Payment payment) {
            return of(payment, "APPROVED");
        }

        // 같은 결제의 다음 결과(결제 서버가 뒤에 남겨 번호가 더 크다). 만든 시각은 앞 결과에 gap 을 더한 때다
        Event next(String nextStatus, Duration gap) {
            return new Event(payment, EVENT_NUMBERS.incrementAndGet(), nextStatus, occurredAt.plus(gap));
        }

        String key() {
            return payment.key();
        }

        String orderId() {
            return payment.orderId;
        }

        String body() {
            return "{\"eventId\":\"" + eventId + "\",\"paymentId\":" + payment.paymentId + ",\"orderId\":\"" + payment.orderId
                    + "\",\"status\":\"" + status + "\",\"occurredAt\":\"" + occurredAt + "\"}";
        }
    }
}
