package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.OtherPaymentServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.comparison.NetCancelReconciler;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.application.payment.dto.ReceiveWebhookCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 서버 여러 대에서 주기 작업(4장 승인 보정, 5장 취소 보정, 6장 받은 알림 처리)이 함께 돌 때의 규칙(7장).
 * 서버끼리 작업을 어떻게 나누는지(나누지 않기, 작업 잠금으로 한 대만, 결제마다 점유)가 구현마다 다르므로 구현마다 이 클래스를 상속해
 * 같은 테스트를 돌린다. 시험 서버 한 대에 같은 MySQL·Mock PG 를 쓰는 결제 서버를 한 대 더 띄우고(OtherPaymentServer),
 * 두 서버가 같은 때에 주기 작업을 한 번씩 시작한다. 카드사는 조회·취소에 0.5초 늦게 답한다 — 한 서버가 작업을 마치기 전에
 * 다른 서버도 작업을 시작한다. 대상은 결제 세 건이다.
 * 결제 금액 15000원, 부분 취소 5000원, PG 읽기 시간 제한 1초, 늦은 답 1.5초, 보정 대기 시간 10초는 4·5장 계약과 같다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, NetCancelReconciler.class})
abstract class MultiServerContract {

    static final Duration LATE = Duration.ofMillis(1500);
    static final Duration SLOW_FOLLOW_UP = Duration.ofMillis(500);
    static final Duration WAIT_AFTER_REQUEST = Duration.ofSeconds(10);
    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final BigDecimal PART = new BigDecimal("5000");
    static final int TARGETS = 3;
    static final List<String> SERVER_PROPERTIES = List.of(
            "payment.pg.read-timeout=1s",
            "payment.reconciliation.wait-after-request=10s");

    private static ConfigurableApplicationContext otherServer;

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
    PaymentCancelService paymentCancelService;

    @Autowired
    PaymentWebhookService paymentWebhookService;

    @Autowired
    PaymentQueryService paymentQueryService;

    /** 서버 한 대에서 보정 주기(승인 보정 뒤 취소 보정)를 한 번 돌린다 */
    abstract void reconcileOn(ApplicationContext server, Instant now);

    /** 서버 한 대에서 받은 알림 처리 주기를 한 번 돌린다 */
    abstract void processWebhooksOn(ApplicationContext server);

    /** 서버 한 대에서 망취소(4장 비교 A) 보정 주기를 한 번 돌린다 */
    abstract void netCancelOn(ApplicationContext server, Instant now);

    /** 다른 서버에도 올릴 시험 부품 */
    Class<?>[] serverComponents() {
        return new Class<?>[] {NetCancelReconciler.class};
    }

    @BeforeEach
    void startOtherServer() {
        if (Objects.isNull(otherServer)) {
            otherServer = OtherPaymentServer.start(mysql, mockPg, SERVER_PROPERTIES, serverComponents());
        }
        // 앞 테스트가 남긴 대상이 이번 주기 작업에 섞이지 않게 한다
        jdbcTemplate.update("delete from payment_webhook");
        jdbcTemplate.update("delete from payment_cancel");
        // 8장부터 결제에 담긴 상품도 결제를 가리킨다
        jdbcTemplate.update("delete from payment_item");
        jdbcTemplate.update("delete from payment");
    }

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
    }

    @AfterAll
    static void stopOtherServer() {
        if (Objects.nonNull(otherServer)) {
            otherServer.close();
            otherServer = null;
        }
    }

    @Test
    @DisplayName("서버 두 대가 같은 때 보정을 돌려도 결과 모름 결제마다 카드사 조회는 한 번이다")
    void unknownPaymentsAreLookedUpOnceByTwoServers() {
        List<String> orderIds = unknownPayments();
        mockPg.answerFollowUpsAfter(SLOW_FOLLOW_UP);

        onBothServers(server -> reconcileOn(server, afterWaiting()));

        assertThat(orderIds).extracting(mockPg::inquiryCount).as("결제마다 카드사 조회 횟수").containsOnly(1);
    }

    @Test
    @DisplayName("서버 두 대가 같은 때 보정을 돌려도 결과 모름 결제는 카드사와 같은 승인으로 맞춰진다")
    void unknownPaymentsAreApprovedByTwoServers() {
        List<String> orderIds = unknownPayments();
        mockPg.answerFollowUpsAfter(SLOW_FOLLOW_UP);

        onBothServers(server -> reconcileOn(server, afterWaiting()));

        assertThat(orderIds).extracting(this::statusOf).containsOnly(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("서버 두 대가 같은 때 보정을 돌려도 결과 모름 취소가 있는 결제마다 카드사 조회는 한 번이다")
    void unknownCancelsAreLookedUpOnceByTwoServers() {
        List<String> orderIds = unknownCancels();
        mockPg.answerFollowUpsAfter(SLOW_FOLLOW_UP);

        onBothServers(server -> reconcileOn(server, afterWaiting()));

        assertThat(orderIds).extracting(mockPg::inquiryCount).as("결제마다 카드사 조회 횟수").containsOnly(1);
    }

    @Test
    @DisplayName("서버 두 대가 같은 때 받은 알림을 처리해도 알림이 온 결제마다 카드사 조회는 한 번이다")
    void noticedPaymentsAreLookedUpOnceByTwoServers() {
        List<String> orderIds = paymentsCanceledAtCardCompanyWithNotice();
        mockPg.answerFollowUpsAfter(SLOW_FOLLOW_UP);

        onBothServers(this::processWebhooksOn);

        assertThat(orderIds).extracting(mockPg::inquiryCount).as("결제마다 카드사 조회 횟수").containsOnly(1);
    }

    @Test
    @DisplayName("서버 두 대가 같은 때 받은 알림을 처리해도 남은 금액은 카드사 잔액과 같다")
    void noticedPaymentsMatchBalanceByTwoServers() {
        List<String> orderIds = paymentsCanceledAtCardCompanyWithNotice();
        mockPg.answerFollowUpsAfter(SLOW_FOLLOW_UP);

        onBothServers(this::processWebhooksOn);

        assertThat(orderIds).extracting(this::balanceOf).usingElementComparator(BigDecimal::compareTo)
                .containsExactlyElementsOf(orderIds.stream().map(mockPg::balanceAmount).toList());
    }

    @Test
    @DisplayName("서버 두 대가 같은 때 망취소 보정을 돌려도 결과 모름 결제마다 카드사에 취소는 한 번만 보낸다")
    void netCancelIsSentOnceByTwoServers() {
        List<String> orderIds = unknownPayments();
        mockPg.answerFollowUpsAfter(SLOW_FOLLOW_UP);

        onBothServers(server -> netCancelOn(server, afterWaiting()));

        assertThat(orderIds).extracting(mockPg::cancelRequestCount).as("결제마다 카드사에 보낸 취소 횟수").containsOnly(1);
    }

    // 두 서버가 같은 때에 작업을 시작하고, 둘 다 끝날 때까지 기다린다. 한 서버의 작업이 실패해도 다른 서버를 기다린다
    void onBothServers(Consumer<ApplicationContext> job) {
        onServers(List.of(thisServer, otherServer), job);
    }

    static void onServers(List<ApplicationContext> servers, Consumer<ApplicationContext> job) {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Void>> running = servers.stream()
                .map(server -> CompletableFuture.runAsync(() -> {
                    awaitStart(start);
                    job.accept(server);
                }))
                .toList();
        start.countDown();
        running.forEach(each -> catchThrowable(each::join));
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    // 카드사는 승인했지만 답이 시간 제한을 넘겨 결과 모름으로 남은 결제들
    List<String> unknownPayments() {
        List<String> orderIds = IntStream.range(0, TARGETS).mapToObj(each -> prepare()).toList();
        mockPg.respondAfter(LATE);
        orderIds.stream()
                .map(orderId -> CompletableFuture.runAsync(() -> catchThrowable(() -> paymentConfirmService.confirm(
                        new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED)))))
                .toList()
                .forEach(CompletableFuture::join);
        orderIds.forEach(this::awaitCardCompany);
        mockPg.reset();
        assertThat(orderIds).extracting(this::statusOf).as("보정 전 결제 상태").containsOnly(PaymentStatus.APPROVAL_UNKNOWN);
        return orderIds;
    }

    // 승인된 결제에 보낸 부분 취소의 답이 시간 제한을 넘겨 결과 모름으로 남았고, 카드사는 그 뒤에 취소를 마쳤다
    List<String> unknownCancels() {
        List<String> orderIds = IntStream.range(0, TARGETS).mapToObj(each -> approvedOrder()).toList();
        mockPg.answerFollowUpsAfter(LATE);
        orderIds.stream()
                .map(orderId -> CompletableFuture.runAsync(() -> catchThrowable(() -> paymentCancelService.cancel(
                        new CancelPaymentCommand(orderId, "cancel-1-" + orderId, PART, "Customer changed their mind")))))
                .toList()
                .forEach(CompletableFuture::join);
        orderIds.forEach(this::awaitCardCompany);
        mockPg.reset();
        return orderIds;
    }

    // 승인된 결제를 가맹점 관리 화면에서 부분 취소했고, 그 알림을 받아 두었다
    List<String> paymentsCanceledAtCardCompanyWithNotice() {
        List<String> orderIds = IntStream.range(0, TARGETS).mapToObj(each -> approvedOrder()).toList();
        orderIds.forEach(orderId -> {
            mockPg.cancelAtCardCompany(orderId, PART);
            paymentWebhookService.receive(new ReceiveWebhookCommand("PAYMENT_STATUS_CHANGED", orderId, "PARTIAL_CANCELED",
                    "2026-11-17T10:00:00.000000"));
        });
        return orderIds;
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    String approvedOrder() {
        String orderId = prepare();
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        return orderId;
    }

    // 방금 요청한 결제·취소도 보정 대기 시간이 지난 것으로 보는 때
    static Instant afterWaiting() {
        return Instant.now().plus(WAIT_AFTER_REQUEST).plusSeconds(1);
    }

    PaymentStatus statusOf(String orderId) {
        return paymentQueryService.find(orderId).status();
    }

    BigDecimal balanceOf(String orderId) {
        BigDecimal canceled = paymentQueryService.findCancels(orderId).stream()
                .filter(cancel -> cancel.cancelStatus().isDone())
                .map(CancelResult::cancelAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return PREPARED.subtract(canceled);
    }

    // 결제 서버가 기다리기를 그만둔 뒤에도 카드사는 처리를 이어 간다. 그 처리가 끝날 때까지 기다린다
    void awaitCardCompany(String orderId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId), Predicate.isEqual(false));
    }
}
