package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.OtherPaymentServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.comparison.NetCancelReconciler;
import com.example.payment.application.payment.comparison.RowClaimJobs;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 7장 결정 5 측정: 서버 수(1·2·3대)마다 같은 때 승인 보정 주기를 한 번 돌리고 카드사 조회 횟수와 걸린 시간을 센다.
 * 대상은 결과 모름 결제 20건(카드사는 모두 승인), 카드사는 조회에 0.1초 늦게 답한다. 구현은 작업 잠금(운영), 나누지 않음,
 * 결제마다 점유. 묶음마다 2번 돌린다. 결과는 build/measurement/ch07-concurrency.txt 에 덧붙인다.
 */
@Tag("concurrency-measurement")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, NetCancelReconciler.class, RowClaimJobs.class})
@DisplayName("서버 수별 주기 작업 측정")
class JobMeasurement {

    private static final Duration LATE = Duration.ofMillis(1500);
    private static final BigDecimal PREPARED = new BigDecimal("15000");
    private static final int TARGETS = 20;
    private static final int ROUNDS = 2;

    private static final List<ConfigurableApplicationContext> OTHER_SERVERS = new ArrayList<>();

    @Autowired
    ApplicationContext thisServer;

    @Autowired
    MySQLContainer mysql;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentQueryService paymentQueryService;

    @AfterAll
    static void stopOtherServers() {
        OTHER_SERVERS.forEach(ConfigurableApplicationContext::close);
        OTHER_SERVERS.clear();
    }

    @Test
    void measure() {
        IntStream.range(0, 2).forEach(each -> OTHER_SERVERS.add(OtherPaymentServer.start(mysql, mockPg,
                MultiServerContract.SERVER_PROPERTIES, NetCancelReconciler.class, RowClaimJobs.class)));
        Map<String, BiConsumer<ApplicationContext, Instant>> implementations = Map.of(
                "작업 잠금(운영)", (server, now) -> server.getBean(ExclusiveJobRunner.class)
                        .run(ExclusiveJobRunner.RECONCILIATION, () -> server.getBean(PaymentReconciler.class).reconcile(now)),
                "나누지 않음", (server, now) -> server.getBean(PaymentReconciler.class).reconcile(now),
                "결제마다 점유", (server, now) -> server.getBean(RowClaimJobs.class).reconcile(now));
        List.of("작업 잠금(운영)", "나누지 않음", "결제마다 점유").forEach(name -> IntStream.rangeClosed(1, 3)
                .forEach(servers -> IntStream.rangeClosed(1, ROUNDS)
                        .forEach(round -> measure(name, implementations.get(name), servers, round))));
    }

    private void measure(String name, BiConsumer<ApplicationContext, Instant> reconcile, int servers, int round) {
        jdbcTemplate.update("delete from payment_cancel");
        // 8장부터 결제에 담긴 상품도 결제를 가리킨다
        jdbcTemplate.update("delete from payment_item");
        jdbcTemplate.update("delete from payment_webhook");
        jdbcTemplate.update("delete from payment");
        List<String> orderIds = unknownPayments();
        mockPg.answerFollowUpsAfter(Duration.ofMillis(100));
        List<ApplicationContext> running = new ArrayList<>();
        running.add(thisServer);
        running.addAll(OTHER_SERVERS.subList(0, servers - 1));
        Instant now = Instant.now().plusSeconds(11);
        long begin = System.nanoTime();
        MultiServerContract.onServers(running, server -> reconcile.accept(server, now));
        long millis = (System.nanoTime() - begin) / 1_000_000;
        mockPg.reset();
        int inquiries = orderIds.stream().mapToInt(mockPg::inquiryCount).sum();
        long approved = orderIds.stream().filter(orderId -> statusOf(orderId) == PaymentStatus.APPROVED).count();
        LockMeasurement.write(String.format("%s | 서버 %d대 #%d | 결과 모름 결제 %d건 | 카드사 조회 %d번 | 승인으로 맞춰진 결제 %d건 | 주기 한 번 %dms",
                name, servers, round, orderIds.size(), inquiries, approved, millis));
    }

    // 카드사는 승인했지만 답이 시간 제한을 넘겨 결과 모름으로 남은 결제들
    private List<String> unknownPayments() {
        List<String> orderIds = IntStream.range(0, TARGETS).mapToObj(each -> prepare()).toList();
        mockPg.respondAfter(LATE);
        ExecutorService threads = Executors.newFixedThreadPool(TARGETS);
        try {
            orderIds.stream()
                    .map(orderId -> CompletableFuture.runAsync(() -> catchThrowable(() -> paymentConfirmService.confirm(
                            new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED))), threads))
                    .toList()
                    .forEach(CompletableFuture::join);
        } finally {
            threads.shutdown();
        }
        orderIds.forEach(orderId -> await().atMost(Duration.ofSeconds(5))
                .until(() -> mockPg.isWaiting(orderId), Predicate.isEqual(false)));
        mockPg.reset();
        Map<PaymentStatus, Long> statuses = orderIds.stream()
                .collect(Collectors.groupingBy(this::statusOf, Collectors.counting()));
        if (statuses.getOrDefault(PaymentStatus.APPROVAL_UNKNOWN, 0L) != TARGETS) {
            throw new IllegalStateException("Payments before reconciliation: " + statuses);
        }
        return orderIds;
    }

    private String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    private PaymentStatus statusOf(String orderId) {
        return paymentQueryService.find(orderId).status();
    }
}
