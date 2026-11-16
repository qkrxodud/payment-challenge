package com.example.payment.presentation.webhook;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.mockpg.WebhookAttempt;
import com.example.mockpg.WebhookDelivery;
import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.PaymentCancelReconciler;
import com.example.payment.application.payment.PaymentCancelService;
import com.example.payment.application.payment.PaymentConfirmService;
import com.example.payment.application.payment.PaymentPrepareService;
import com.example.payment.application.payment.PaymentQueryService;
import com.example.payment.application.payment.PaymentReconciler;
import com.example.payment.application.payment.PaymentWebhookProcessor;
import com.example.payment.application.payment.PaymentWebhookRecorder;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.domain.webhook.PaymentWebhookRepository;
import com.example.payment.infrastructure.pg.LastCancelOnlyPaymentGateway;
import com.example.payment.presentation.webhook.comparison.WebhookComparisonConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * 6장 측정: 구현마다 같은 알림 묶음을 보내고, 받은 알림 처리(0.2초마다)와 4·5장 주기 보정(0.2초마다, 대기 시간 10초)을 함께 돌리며
 * 결제가 카드사와 맞춰지기까지를 잰다. 기본 빌드에서 빼고 ./gradlew webhookMeasurement 로 돌린다.
 * 결제는 모두 15000원, 부분 취소는 5000원. 묶음(시나리오마다 결제 2건, 모두 18건):
 * - 순서 바뀜: 결과 모름 결제를 카드사가 승인한 뒤 전액 취소, 취소 알림이 먼저
 * - 승인 알림이 승인 답보다 먼저
 * - 중복: 카드사에서만 일어난 부분 취소 알림을 같은 본문으로 두 번
 * - 같은 상태 다시: 카드사에서만 일어난 부분 취소 뒤 같은 상태를 새 알림으로 한 번 더
 * - 위조: 승인된 결제에 전액 취소 위조 알림
 * - 늦은 조회: 카드사 조회가 1.5초 늦는 동안 카드사에서만 일어난 부분 취소
 * - 결과 모름 결제: 승인 답이 늦어 시간 초과, 카드사는 승인
 * - 결과 모름 취소: 우리 취소 답이 늦어 시간 초과, 카드사는 취소
 * - 카드사에서만 일어난 부분 취소
 * 읽기 시간 제한 1초. 알림은 200 이 아니면 100ms 간격으로 7번까지 다시 보낸다(토스페이먼츠는 1분~4096분 간격).
 * "맞춰지기까지"는 그 결제의 마지막 카드사 쪽 변화(또는 위조 알림)부터 처음 카드사와 같아진 때까지다(0.1초마다 봄).
 * 묶음을 다 보낸 뒤 25초까지 보고, 그때 카드사와 다른 결제는 "어긋남"이다.
 * 결과는 build/measurement/ch06-webhook.txt 에 덧붙인다.
 */
@Tag("webhook-measurement")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, WebhookComparisonConfiguration.class})
@DisplayName("카드사 웹훅 처리 측정")
class WebhookMeasurement {

    private static final Duration LATE = Duration.ofMillis(1500);
    private static final Duration WATCH = Duration.ofSeconds(25);
    private static final BigDecimal PREPARED = new BigDecimal("15000");
    private static final BigDecimal PART = new BigDecimal("5000");
    private static final Path OUTPUT = Path.of("build", "measurement", "ch06-webhook.txt");
    private static final Map<String, Set<PaymentStatus>> PAIRS = Map.of(
            "DONE", Set.of(PaymentStatus.APPROVED, PaymentStatus.CAPTURED),
            "PARTIAL_CANCELED", Set.of(PaymentStatus.APPROVED, PaymentStatus.CAPTURED),
            "CANCELED", Set.of(PaymentStatus.APPROVAL_CANCELED, PaymentStatus.CAPTURE_CANCELED),
            "ABORTED", Set.of(PaymentStatus.APPROVAL_FAILED));

    @LocalServerPort
    int port;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentCancelService paymentCancelService;

    @Autowired
    PaymentQueryService paymentQueryService;

    @Autowired
    PaymentReconciler paymentReconciler;

    @Autowired
    PaymentCancelReconciler paymentCancelReconciler;

    @Autowired
    PaymentWebhookProcessor paymentWebhookProcessor;

    @Autowired
    PaymentWebhookRepository paymentWebhookRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentWebhookRecorder paymentWebhookRecorder;

    @Autowired
    RestClient pgRestClient;

    @AfterEach
    void resetMockPg() {
        mockPg.resetWebhooks();
        mockPg.reset();
    }

    @Test
    @DisplayName("운영 구현: 받아 남기고 바로 답한 뒤, 처리기가 카드사 조회로 맞춘다")
    void operation() {
        measure("운영 구현", "/api/v1/webhooks/tosspayments", paymentWebhookProcessor::process);
    }

    @Test
    @DisplayName("비교 A: 본문의 상태를 그대로 믿는다")
    void bodyStatus() {
        measure("비교 A 본문 상태를 그대로 믿음", WebhookComparisonConfiguration.BODY_STATUS, () -> { });
    }

    @Test
    @DisplayName("비교 B: 허용 전이 표에 없으면 오류로 답한다")
    void rejectOffTable() {
        measure("비교 B 표에 없는 전이는 오류로 답함", WebhookComparisonConfiguration.REJECT_OFF_TABLE, () -> { });
    }

    @Test
    @DisplayName("비교 C: 본문을 믿고 알림 시각이 가장 늦은 알림만 받는다")
    void latestCreatedAt() {
        measure("비교 C createdAt 으로 최신만 받음", WebhookComparisonConfiguration.LATEST_CREATED_AT, () -> { });
    }

    @Test
    @DisplayName("비교 D: 같은 본문을 거르고 알림 한 건을 변화 한 건으로 처리한다")
    void bodyDigest() {
        PaymentWebhookProcessor processor = new PaymentWebhookProcessor(paymentWebhookRepository, paymentRepository,
                new LastCancelOnlyPaymentGateway(pgRestClient), paymentWebhookRecorder);
        measure("비교 D 본문 해시로 거름", WebhookComparisonConfiguration.BODY_DIGEST, processor::process);
    }

    @Test
    @DisplayName("비교 E: 받은 요청 안에서 조회와 반영까지 끝내고 답한다")
    void inRequest() {
        measure("비교 E 요청 안에서 조회·반영", WebhookComparisonConfiguration.IN_REQUEST, () -> { });
    }

    @Test
    @DisplayName("비교 F: 알림을 무시하고 주기 보정만 한다")
    void ignored() {
        measure("비교 F 알림 무시, 주기 보정만", WebhookComparisonConfiguration.IGNORED, () -> { });
    }

    private void measure(String name, String path, Runnable process) {
        // 앞 측정이 남긴 처리 전 알림이 이번 처리에 섞이지 않게 닫아 둔다
        jdbcTemplate.update("update payment_webhook set status = 'IGNORED' where status = 'RECEIVED'");
        mockPg.sendWebhooksTo("http://localhost:" + port + path);
        Map<String, String> scenarioOf = new LinkedHashMap<>();
        Map<String, Instant> changedAt = new ConcurrentHashMap<>();
        Map<String, Instant> matchedAt = new ConcurrentHashMap<>();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        scheduler.scheduleWithFixedDelay(() -> runQuietly(process), 0, 200, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(() -> recordMatches(changedAt, matchedAt), 0, 100, TimeUnit.MILLISECONDS);
        Instant started = Instant.now();
        try {
            repeat(() -> reversedNotices(scenarioOf, changedAt));
            repeat(() -> approvalNoticeBeforeAnswer(scenarioOf, changedAt));
            repeat(() -> duplicatedNotice(scenarioOf, changedAt));
            repeat(() -> sameStatusAgain(scenarioOf, changedAt));
            repeat(() -> forgedNotice(scenarioOf, changedAt));
            repeat(() -> slowLookup(scenarioOf, changedAt));
            repeat(() -> unknownApproval(scenarioOf, changedAt));
            repeat(() -> unknownCancel(scenarioOf, changedAt));
            repeat(() -> gatewayOnlyCancel(scenarioOf, changedAt));
            Instant sent = Instant.now();
            Map<String, Duration> matchedAfter = watch(scenarioOf.keySet(), changedAt, matchedAt, sent);
            write(report(name, scenarioOf, matchedAfter, Duration.between(started, sent)));
        } finally {
            scheduler.shutdownNow();
        }
    }

    // 받은 알림 처리(구현마다 다름)와 4·5장 주기 보정(모든 구현 같음)을 함께 돌린다
    private void runQuietly(Runnable process) {
        try {
            process.run();
            paymentReconciler.reconcile(Instant.now());
            paymentCancelReconciler.reconcile(Instant.now());
        } catch (RuntimeException ignored) {
            // 측정 중 한 번 실패해도 다음 주기에 다시 돈다
        }
    }

    private void reversedNotices(Map<String, String> scenarioOf, Map<String, Instant> changedAt) {
        String orderId = prepare(scenarioOf, "순서 바뀜(취소 알림이 승인 알림보다 먼저)");
        mockPg.holdWebhooks();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> paymentConfirmService.confirm(confirmRequest(orderId)));
        awaitCardCompany(orderId);
        mockPg.reset();
        mockPg.cancelAtCardCompany(orderId);
        changedAt.put(orderId, Instant.now());
        mockPg.releaseWebhooks(true);
    }

    private void approvalNoticeBeforeAnswer(Map<String, String> scenarioOf, Map<String, Instant> changedAt) {
        String orderId = prepare(scenarioOf, "승인 알림이 승인 답보다 먼저");
        mockPg.holdConfirmAnswers();
        CompletableFuture<Void> confirming = CompletableFuture.runAsync(
                () -> catchThrowable(() -> paymentConfirmService.confirm(confirmRequest(orderId))));
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.webhookDeliveries(orderId).size() > 0);
        changedAt.put(orderId, Instant.now());
        // 처리 주기(0.2초)가 한 번은 돌 만큼 답을 붙잡아 둔다
        sleep(Duration.ofMillis(500));
        mockPg.releaseConfirmAnswers();
        confirming.join();
    }

    private void duplicatedNotice(Map<String, String> scenarioOf, Map<String, Instant> changedAt) {
        String orderId = approvedOrder(scenarioOf, "같은 알림 두 번");
        mockPg.duplicateWebhooks();
        mockPg.cancelAtCardCompany(orderId, PART);
        changedAt.put(orderId, Instant.now());
        mockPg.stopDuplicatingWebhooks();
    }

    private void sameStatusAgain(Map<String, String> scenarioOf, Map<String, Instant> changedAt) {
        String orderId = approvedOrder(scenarioOf, "같은 상태를 다른 때에 다시");
        mockPg.cancelAtCardCompany(orderId, PART);
        changedAt.put(orderId, Instant.now());
        sleep(Duration.ofMillis(500));
        mockPg.resendWebhook(orderId);
    }

    private void forgedNotice(Map<String, String> scenarioOf, Map<String, Instant> changedAt) {
        String orderId = approvedOrder(scenarioOf, "위조 알림(전액 취소)");
        changedAt.put(orderId, Instant.now());
        mockPg.forgeWebhook(orderId, "CANCELED", PREPARED);
    }

    private void slowLookup(Map<String, String> scenarioOf, Map<String, Instant> changedAt) {
        String orderId = approvedOrder(scenarioOf, "카드사 조회가 늦음(1.5초)");
        mockPg.answerFollowUpsAfter(LATE);
        mockPg.cancelAtCardCompany(orderId, PART);
        changedAt.put(orderId, Instant.now());
        sleep(LATE);
        mockPg.reset();
    }

    private void unknownApproval(Map<String, String> scenarioOf, Map<String, Instant> changedAt) {
        String orderId = prepare(scenarioOf, "결과 모름 결제");
        mockPg.respondAfter(LATE);
        catchThrowable(() -> paymentConfirmService.confirm(confirmRequest(orderId)));
        awaitCardCompany(orderId);
        changedAt.put(orderId, Instant.now());
        mockPg.reset();
    }

    private void unknownCancel(Map<String, String> scenarioOf, Map<String, Instant> changedAt) {
        String orderId = approvedOrder(scenarioOf, "결과 모름 취소");
        mockPg.answerFollowUpsAfter(LATE);
        catchThrowable(() -> paymentCancelService.cancel(
                new CancelPaymentCommand(orderId, "cancel-1-" + orderId, PART, "Customer changed their mind")));
        awaitCardCompany(orderId);
        changedAt.put(orderId, Instant.now());
        mockPg.reset();
    }

    private void gatewayOnlyCancel(Map<String, String> scenarioOf, Map<String, Instant> changedAt) {
        String orderId = approvedOrder(scenarioOf, "카드사에서만 일어난 부분 취소");
        mockPg.cancelAtCardCompany(orderId, PART);
        changedAt.put(orderId, Instant.now());
    }

    // 카드사 쪽 변화가 끝난 결제가 처음 카드사와 같아진 때를 0.1초마다 적는다
    private void recordMatches(Map<String, Instant> changedAt, Map<String, Instant> matchedAt) {
        try {
            changedAt.keySet().stream()
                    .filter(orderId -> Objects.isNull(matchedAt.get(orderId)))
                    .filter(this::matchesCardCompany)
                    .forEach(orderId -> matchedAt.put(orderId, Instant.now()));
        } catch (RuntimeException ignored) {
            // 다음 주기에 다시 본다
        }
    }

    // 묶음을 다 보낸 뒤 25초까지 기다린 뒤의 결과. 맞춰진 뒤 다시 어긋난 결제(위조 알림을 늦게 반영 등)와
    // 끝내 맞춰지지 않은 결제는 빠진다. 값은 카드사 쪽 마지막 변화부터 처음 맞춰진 때까지
    private Map<String, Duration> watch(Set<String> orders, Map<String, Instant> changedAt, Map<String, Instant> matchedAt,
                                        Instant sent) {
        while (Duration.between(sent, Instant.now()).compareTo(WATCH) < 0 && matchedAt.size() < orders.size()) {
            sleep(Duration.ofMillis(100));
        }
        Map<String, Duration> matchedAfter = new LinkedHashMap<>();
        orders.stream()
                .filter(orderId -> Objects.nonNull(matchedAt.get(orderId)))
                .filter(this::matchesCardCompany)
                .forEach(orderId -> matchedAfter.put(orderId,
                        Duration.between(changedAt.get(orderId), matchedAt.get(orderId))));
        return matchedAfter;
    }

    private String report(String name, Map<String, String> scenarioOf, Map<String, Duration> matchedAfter, Duration sending) {
        List<WebhookDelivery> deliveries = scenarioOf.keySet().stream()
                .flatMap(orderId -> mockPg.webhookDeliveries(orderId).stream())
                .toList();
        long firstNotOk = deliveries.stream().filter(WebhookMeasurement::isFirstNotOk).count();
        long retries = deliveries.stream().mapToLong(delivery -> Math.max(0, delivery.getAttempts().size() - 1)).sum();
        long neverOk = deliveries.stream().filter(Predicate.not(WebhookDelivery::isDelivered)).count();
        long slowestFirst = deliveries.stream()
                .filter(delivery -> delivery.getAttempts().size() > 0)
                .mapToLong(delivery -> delivery.getAttempts().get(0).getElapsedMillis())
                .max()
                .orElse(0);
        List<String> lines = new ArrayList<>();
        lines.add("## " + name);
        lines.add("결제 " + scenarioOf.size() + "건, 카드사와 어긋난 결제(25초 뒤) " + (scenarioOf.size() - matchedAfter.size()) + "건");
        lines.add("알림 " + deliveries.size() + "건, 첫 전송에 200 을 받지 못한 알림 " + firstNotOk + "건, 재전송 " + retries
                + "번, 재전송을 다 써도 200 을 받지 못한 알림 " + neverOk + "건, 가장 늦은 첫 답 " + slowestFirst + "ms");
        lines.add("묶음을 보내는 데 " + sending.toMillis() + "ms");
        Map<String, List<String>> byScenario = new LinkedHashMap<>();
        scenarioOf.forEach((orderId, scenario) -> byScenario.computeIfAbsent(scenario, key -> new ArrayList<>())
                .add(describe(matchedAfter.get(orderId))));
        byScenario.forEach((scenario, results) -> lines.add("- " + scenario + ": " + String.join(", ", results)));
        lines.add("");
        return String.join("\n", lines);
    }

    private static boolean isFirstNotOk(WebhookDelivery delivery) {
        List<WebhookAttempt> attempts = delivery.getAttempts();
        return attempts.isEmpty() || Predicate.not(WebhookAttempt::isOk).test(attempts.get(0));
    }

    private static String describe(Duration matched) {
        if (Objects.isNull(matched)) {
            return "어긋남";
        }
        return matched.toMillis() + "ms";
    }

    private boolean matchesCardCompany(String orderId) {
        String cardCompany = mockPg.paymentState(orderId);
        return PAIRS.getOrDefault(cardCompany, Set.of()).contains(paymentQueryService.find(orderId).status())
                && balanceOf(orderId).compareTo(mockPg.balanceAmount(orderId)) == 0;
    }

    private BigDecimal balanceOf(String orderId) {
        return PREPARED.subtract(paymentQueryService.findCancels(orderId).stream()
                .filter(cancel -> cancel.cancelStatus().isDone())
                .map(CancelResult::cancelAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private String prepare(Map<String, String> scenarioOf, String scenario) {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        scenarioOf.put(orderId, scenario);
        return orderId;
    }

    // 승인해 둔 결제. 승인 알림도 카드사가 보낸다
    private String approvedOrder(Map<String, String> scenarioOf, String scenario) {
        String orderId = prepare(scenarioOf, scenario);
        paymentConfirmService.confirm(confirmRequest(orderId));
        return orderId;
    }

    private static ConfirmPaymentCommand confirmRequest(String orderId) {
        return new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED);
    }

    private void awaitCardCompany(String orderId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId), Predicate.isEqual(false));
    }

    private static void repeat(Runnable scenario) {
        scenario.run();
        scenario.run();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void write(String text) {
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, text + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
