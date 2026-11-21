package com.example.payment.application.payment;

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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 11장 측정(./gradlew consumerMeasurement — 기본 check 에서 뺐다). 주문 서비스(결제 결과 소비자)를 방식마다 같은 묶음으로 돌린다.
 * 다시 시도 간격·횟수는 운영 값(1·2·4·8초, order-service.properties)이다 — 계약에서 줄인 값이 아니다.
 * 가. 받는 쪽 DB 장애: MySQL 을 outage 초 동안 읽기 전용으로 돌리고, 그동안 0.5초마다 새 결제의 승인 결과를 하나씩 싣는다(키가 다른 결제, 파티션 2개에 흩어짐).
 *     장애를 걷은 뒤 모든 결과가 반영되거나 실패 모음에 남을 때까지(또는 60초) 기다린다.
 *     반영: 주문이 결제 완료가 된 수. 실패 모음: DLT(또는 실패 표·다시 시도 토픽의 DLT)에 남은 수. 잃음: 둘 다 아닌 수.
 *     반영 지연: 결과를 토픽에 실은 때(브로커 시각)부터 주문 서비스가 받아 남긴 때까지(반영된 것만, 중앙·p95·최대).
 * 나. 막는 시간: 한 결제의 결과를 받는 쪽 DB 가 계속 쓰지 못하게(트리거) 한 채 그 결과를 싣고, 바로 뒤에 같은 파티션의 다른 결제 결과를 싣는다.
 *     뒤 결과가 반영되기까지 걸린 시간. 모르는 결제 상태(영구 오류) 뒤에 실은 결과도 같은 방법으로 잰다.
 * 방식마다 3번(#1~#3). 결과는 build/measurement/ch11-consumer.txt 에 덧붙인다.
 */
@Tag("consumer-measurement")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, OrderServerConfiguration.class})
abstract class ConsumerMeasurement {

    static final Path OUTPUT = Path.of("build", "measurement", "ch11-consumer.txt");
    static final int RUNS = 3;
    static final int OUTAGE_EVENTS_PER_SECOND = 2;
    static final Duration SETTLE = Duration.ofSeconds(60);
    static final Duration BLOCK_LIMIT = Duration.ofSeconds(60);
    private static final AtomicLong EVENT_NUMBERS = new AtomicLong(1_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000L) * 1000);

    @Autowired
    OrderServer orderServer;

    @Autowired
    MySQLContainer mysqlContainer;

    MessageFeed feed;

    ReceiverOutage outage;

    abstract String method();

    // 실패 모음에 남은 본문. 운영·기본·로그만은 <토픽>.DLT, 다시 시도 토픽도 <토픽>.DLT, 실패 표 방식은 바꾼다
    Set<String> deadLetterBodies() {
        return DeadLetterTopic.read(DeadLetterTopic.of(orderServer.topic())).stream()
                .map(DeadLetter::value)
                .collect(Collectors.toSet());
    }

    @BeforeEach
    void open() {
        feed = MessageFeed.open();
        outage = ReceiverOutage.of(mysqlContainer);
    }

    @AfterEach
    void close() {
        outage.end();
        orderServer.restoreOrderWrites();
        feed.close();
    }

    @Test
    void measure() {
        // 컨텍스트가 뜬 뒤 첫 처리는 늦을 수 있어 한 건을 먼저 반영해 둔다(기록하지 않음)
        Sent warmUp = send(newPayment(), "APPROVED");
        awaitPaid(warmUp);
        for (Duration length : List.of(Duration.ofSeconds(10), Duration.ofSeconds(30))) {
            for (int run = 1; run <= RUNS; run++) {
                write(method() + " | 가. 장애 " + length.toSeconds() + "초 | #" + run + " | " + outage(length));
            }
        }
        for (int run = 1; run <= RUNS; run++) {
            write(method() + " | 나. 막는 시간 | #" + run + " | 일시 오류 뒤 " + blockedBy(true) + "ms / 영구 오류 뒤 " + blockedBy(false) + "ms");
        }
    }

    String outage(Duration length) {
        int count = (int) length.toSeconds() * OUTAGE_EVENTS_PER_SECOND;
        List<Sent> sent = new ArrayList<>();
        outage.begin();
        long started = System.nanoTime();
        for (int index = 0; index < count; index++) {
            sleepUntil(started, Duration.ofMillis(index * 1000L / OUTAGE_EVENTS_PER_SECOND));
            sent.add(send(newPayment(), "APPROVED"));
        }
        sleepUntil(started, length);
        outage.end();
        awaitSettled(sent, count);
        // 늦게 반영되는 것이 더 없는지 조금 더 본다
        sleepUntil(System.nanoTime(), Duration.ofSeconds(2));
        Set<String> dead = deadLetterBodies();
        List<Long> delays = new ArrayList<>();
        int applied = 0;
        int inDeadLetters = 0;
        for (Sent each : sent) {
            Instant receivedAt = receivedAt(each.orderId);
            if (Objects.nonNull(receivedAt)) {
                applied++;
                delays.add(receivedAt.toEpochMilli() - each.sentAtMillis);
                continue;
            }
            if (dead.contains(each.body)) {
                inDeadLetters++;
            }
        }
        return "보냄 " + count + " | 반영 " + applied + " | 실패 모음 " + inDeadLetters + " | 잃음 " + (count - applied - inDeadLetters)
                + " | 반영 지연 ms " + summary(delays);
    }

    // 모두 반영되거나 실패 모음에 남을 때까지 기다린다. 잃은 것이 있으면 SETTLE 까지 기다린 뒤 넘어간다
    private void awaitSettled(List<Sent> sent, int count) {
        try {
            await().atMost(SETTLE).pollInterval(Duration.ofMillis(500)).until(() -> settled(sent) == count);
        } catch (ConditionTimeoutException notAll) {
            // 잃은 것으로 센다
        }
    }

    private int settled(List<Sent> sent) {
        Set<String> dead = deadLetterBodies();
        return (int) sent.stream().filter(each -> Objects.nonNull(receivedAt(each.orderId)) || dead.contains(each.body)).count();
    }

    long blockedBy(boolean transientFailure) {
        Payment failing = newPayment();
        Payment next = newPaymentOnSamePartitionAs(failing);
        if (transientFailure) {
            orderServer.failOrderWrites(failing.orderId, "APPROVED");
        }
        long started = System.nanoTime();
        send(failing, transientFailure ? "APPROVED" : "REFUNDED");
        Sent after = send(next, "APPROVED");
        await().atMost(BLOCK_LIMIT).pollInterval(Duration.ofMillis(20)).until(() -> Objects.nonNull(receivedAt(after.orderId)));
        long blocked = Duration.ofNanos(System.nanoTime() - started).toMillis();
        orderServer.restoreOrderWrites();
        // 막혔던 결과가 끝날 때까지(반영되거나 실패 모음으로 감) 기다려 다음 번에 섞이지 않게 한다
        orderServer.awaitCaughtUp(SETTLE);
        return blocked;
    }

    private Instant receivedAt(String orderId) {
        return orderServer.receivedAtOf(orderId);
    }

    private void awaitPaid(Sent sent) {
        await().atMost(SETTLE).pollInterval(Duration.ofMillis(50)).until(() -> "PAID".equals(orderServer.statusOf(sent.orderId)));
    }

    Sent send(Payment payment, String status) {
        long eventId = EVENT_NUMBERS.incrementAndGet();
        String body = "{\"eventId\":\"" + eventId + "\",\"paymentId\":" + payment.paymentId + ",\"orderId\":\"" + payment.orderId
                + "\",\"status\":\"" + status + "\",\"occurredAt\":\"" + Instant.now() + "\"}";
        long sentAt = feed.send(orderServer.topic(), String.valueOf(payment.paymentId), body);
        return new Sent(payment.orderId, body, sentAt);
    }

    static Payment newPayment() {
        return new Payment(ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L), "order-" + UUID.randomUUID());
    }

    static Payment newPaymentOnSamePartitionAs(Payment other) {
        int partition = MessageFeed.partitionOf(String.valueOf(other.paymentId), KafkaBroker.PARTITIONS);
        Payment candidate = newPayment();
        while (MessageFeed.partitionOf(String.valueOf(candidate.paymentId), KafkaBroker.PARTITIONS) != partition) {
            candidate = newPayment();
        }
        return candidate;
    }

    static String summary(List<Long> values) {
        if (values.isEmpty()) {
            return "없음";
        }
        List<Long> sorted = values.stream().sorted().toList();
        return "중앙 " + sorted.get((sorted.size() - 1) / 2) + "·p95 " + sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1)
                + "·최대 " + sorted.get(sorted.size() - 1) + " (" + sorted.size() + "건)";
    }

    static void sleepUntil(long startedNanos, Duration after) {
        long left = after.toMillis() - Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        if (left > 0) {
            try {
                Thread.sleep(left);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static synchronized void write(String line) {
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /** 측정이 싣는 결제 하나(결제 번호 = 메시지 키, 주문번호) */
    static class Payment {

        private final long paymentId;
        private final String orderId;

        Payment(long paymentId, String orderId) {
            this.paymentId = paymentId;
            this.orderId = orderId;
        }
    }

    /** 실은 결과 한 건: 주문번호, 본문, 브로커가 받은 때 */
    static class Sent {

        private final String orderId;
        private final String body;
        private final long sentAtMillis;

        Sent(String orderId, String body, long sentAtMillis) {
            this.orderId = orderId;
            this.body = body;
            this.sentAtMillis = sentAtMillis;
        }
    }
}
