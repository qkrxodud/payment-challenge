package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.comparison.CurrentStateCancelService;
import com.example.payment.application.payment.comparison.GatewayFirstCancelService;
import com.example.payment.application.payment.comparison.NaturalKeyCancelService;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.CancelStatus;
import com.example.payment.infrastructure.pg.NoIdempotencyKeyPaymentGateway;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * 5장 측정: 구현마다 같은 취소 묶음을 보내고 마지막에 보정을 한 번 돌린 뒤, 카드사 쪽 취소 건수와 잔액을 우리 기록과 비교한다.
 * 기본 빌드에서 빼고 ./gradlew cancelMeasurement 로 돌린다. 결제는 모두 15000원 승인 결제, 부분 취소는 5000원이다.
 * 취소 묶음(결제 12건, 고객이 뜻한 카드사 취소 14건):
 * - 같은 금액 부분 취소를 키를 바꿔 두 번(2건, 뜻한 취소 4)
 * - 부분 취소 뒤 같은 키로 다시(2건, 뜻한 취소 2)
 * - 카드사 취소 답이 늦어 시간 초과, 그 뒤 보정만(2건, 뜻한 취소 2)
 * - 카드사 취소 답이 늦어 시간 초과, 카드사가 처리를 마친 뒤 같은 키로 다시(2건, 뜻한 취소 2)
 * - 카드사 취소 뒤 결과 기록이 실패, 그 뒤 보정만(2건, 뜻한 취소 2)
 * - 전액 취소 뒤 같은 키로 다시(2건, 뜻한 취소 2)
 * 읽기 시간 제한은 1초, 늦은 취소는 1.5초 뒤에 답한다. 결과는 build/measurement/ch05-cancel.txt 에 덧붙인다.
 */
@Tag("cancel-measurement")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class,
        NaturalKeyCancelService.class, CurrentStateCancelService.class, GatewayFirstCancelService.class})
@DisplayName("결제 취소 측정")
class CancelMeasurement {

    private static final Duration LATE = Duration.ofMillis(1500);
    private static final BigDecimal PREPARED = new BigDecimal("15000");
    private static final BigDecimal PART = new BigDecimal("5000");
    private static final String REASON = "Customer changed their mind";
    private static final Path OUTPUT = Path.of("build", "measurement", "ch05-cancel.txt");

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

    @Autowired
    PaymentCancelService paymentCancelService;

    @Autowired
    PaymentCancelRecorder paymentCancelRecorder;

    @Autowired
    PaymentCancelReconciler paymentCancelReconciler;

    @Autowired
    NaturalKeyCancelService naturalKeyCancelService;

    @Autowired
    CurrentStateCancelService currentStateCancelService;

    @Autowired
    GatewayFirstCancelService gatewayFirstCancelService;

    @Autowired
    RestClient pgRestClient;

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
        unblockCancelRecord();
    }

    @Test
    @DisplayName("운영 구현: 멱등키로 가리고, 기록한 뒤 카드사에 멱등키와 함께 보낸다")
    void operation() {
        measure("운영 구현", paymentCancelService::cancel);
    }

    @Test
    @DisplayName("비교 A: 같은 금액·사유를 같은 요청으로 본다")
    void naturalKey() {
        measure("비교 A 자연 키(금액·사유)", naturalKeyCancelService::cancel);
    }

    @Test
    @DisplayName("비교 B: 요청을 기록하지 않고 지금 결제 상태로 답한다")
    void currentState() {
        measure("비교 B 지금 상태로 답함", currentStateCancelService::cancel);
    }

    @Test
    @DisplayName("비교 C: 카드사를 먼저 부르고 답을 받은 뒤에 기록한다")
    void gatewayFirst() {
        measure("비교 C 카드사 먼저 부르고 기록", gatewayFirstCancelService::cancel);
    }

    @Test
    @DisplayName("비교 D: 카드사에 멱등키를 넘기지 않는다")
    void noGatewayKey() {
        PaymentCancelService service = new PaymentCancelService(paymentCancelRecorder,
                new NoIdempotencyKeyPaymentGateway(pgRestClient));
        measure("비교 D 카드사에 멱등키 넘기지 않음", service::cancel);
    }

    private void measure(String name, Function<CancelPaymentCommand, CancelResult> cancel) {
        Map<String, Integer> intended = new TreeMap<>();
        List<String> orders = new ArrayList<>();
        Instant started = Instant.now();

        repeat(2, () -> twoPartialsWithOtherKeys(cancel, orders, intended));
        repeat(2, () -> partialThenSameKey(cancel, orders, intended));
        repeat(2, () -> timeoutThenReconcileOnly(cancel, orders, intended));
        repeat(2, () -> timeoutThenSameKey(cancel, orders, intended));
        repeat(2, () -> failedRecordThenReconcileOnly(cancel, orders, intended));
        repeat(2, () -> fullThenSameKey(cancel, orders, intended));

        paymentCancelReconciler.reconcile(afterWaiting());
        Duration took = Duration.between(started, Instant.now());
        write(report(name, orders, intended, took));
    }

    private void twoPartialsWithOtherKeys(Function<CancelPaymentCommand, CancelResult> cancel, List<String> orders,
                                          Map<String, Integer> intended) {
        String orderId = approvedOrder(orders, intended, 2);
        catchThrowable(() -> cancel.apply(request(orderId, "cancel-1", PART)));
        catchThrowable(() -> cancel.apply(request(orderId, "cancel-2", PART)));
    }

    private void partialThenSameKey(Function<CancelPaymentCommand, CancelResult> cancel, List<String> orders,
                                    Map<String, Integer> intended) {
        String orderId = approvedOrder(orders, intended, 1);
        catchThrowable(() -> cancel.apply(request(orderId, "cancel-1", PART)));
        catchThrowable(() -> cancel.apply(request(orderId, "cancel-1", PART)));
    }

    private void timeoutThenReconcileOnly(Function<CancelPaymentCommand, CancelResult> cancel, List<String> orders,
                                          Map<String, Integer> intended) {
        String orderId = approvedOrder(orders, intended, 1);
        mockPg.answerFollowUpsAfter(LATE);
        catchThrowable(() -> cancel.apply(request(orderId, "cancel-1", PART)));
        awaitCardCompany(orderId);
        mockPg.reset();
    }

    private void timeoutThenSameKey(Function<CancelPaymentCommand, CancelResult> cancel, List<String> orders,
                                    Map<String, Integer> intended) {
        timeoutThenReconcileOnly(cancel, orders, intended);
        String orderId = orders.get(orders.size() - 1);
        catchThrowable(() -> cancel.apply(request(orderId, "cancel-1", PART)));
    }

    private void failedRecordThenReconcileOnly(Function<CancelPaymentCommand, CancelResult> cancel, List<String> orders,
                                               Map<String, Integer> intended) {
        String orderId = approvedOrder(orders, intended, 1);
        blockCancelRecord();
        catchThrowable(() -> cancel.apply(request(orderId, "cancel-1", PART)));
        unblockCancelRecord();
    }

    private void fullThenSameKey(Function<CancelPaymentCommand, CancelResult> cancel, List<String> orders,
                                 Map<String, Integer> intended) {
        String orderId = approvedOrder(orders, intended, 1);
        catchThrowable(() -> cancel.apply(request(orderId, "cancel-1", null)));
        catchThrowable(() -> cancel.apply(request(orderId, "cancel-1", null)));
    }

    private String report(String name, List<String> orders, Map<String, Integer> intended, Duration took) {
        int intendedCancels = intended.values().stream().mapToInt(Integer::intValue).sum();
        int cancels = orders.stream().mapToInt(mockPg::cancelCount).sum();
        int cancelRequests = orders.stream().mapToInt(mockPg::cancelRequestCount).sum();
        long overCanceled = orders.stream().filter(orderId -> mockPg.cancelCount(orderId) > intended.get(orderId)).count();
        long underCanceled = orders.stream().filter(orderId -> mockPg.cancelCount(orderId) < intended.get(orderId)).count();
        long balanceMismatched = orders.stream().filter(this::isBalanceMismatched).count();
        long awaiting = orders.stream().mapToLong(this::awaitingCancels).sum();
        Map<String, Integer> statuses = new TreeMap<>();
        orders.forEach(orderId -> statuses.merge(paymentQueryService.find(orderId).status().name(), 1, Integer::sum));
        Map<String, Integer> cardCompany = new TreeMap<>();
        orders.forEach(orderId -> cardCompany.merge(mockPg.paymentState(orderId), 1, Integer::sum));
        return String.join("\n",
                "## " + name,
                "결제 " + orders.size() + "건, 고객이 뜻한 카드사 취소 " + intendedCancels + "건",
                "카드사가 받은 취소 요청 " + cancelRequests + "건(같은 멱등키로 다시 와 처음 응답을 돌려준 요청 포함), 카드사가 실제로 취소한 건 " + cancels + "건",
                "뜻한 것보다 더 취소된 결제 " + overCanceled + "건, 덜 취소된 결제 " + underCanceled + "건",
                "우리 남은 금액과 카드사 잔액이 다른 결제 " + balanceMismatched + "건",
                "보정 뒤에도 결과를 남기지 못한 취소(요청·결과 모름) " + awaiting + "건",
                "결제 상태 " + statuses + " / 카드사 상태 " + cardCompany,
                "묶음을 보내고 보정까지 " + took.toMillis() + "ms",
                "");
    }

    // 우리 남은 금액: 결제 금액에서 끝난 취소를 뺀 값
    private boolean isBalanceMismatched(String orderId) {
        BigDecimal canceled = paymentQueryService.findCancels(orderId).stream()
                .filter(result -> result.cancelStatus() == CancelStatus.DONE)
                .map(CancelResult::cancelAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return PREPARED.subtract(canceled).compareTo(mockPg.balanceAmount(orderId)) != 0;
    }

    private long awaitingCancels(String orderId) {
        return paymentQueryService.findCancels(orderId).stream()
                .filter(result -> result.cancelStatus().isAwaitingResult())
                .count();
    }

    private String approvedOrder(List<String> orders, Map<String, Integer> intended, int intendedCancels) {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        orders.add(orderId);
        intended.put(orderId, intendedCancels);
        return orderId;
    }

    private static CancelPaymentCommand request(String orderId, String idempotencyKey, BigDecimal amount) {
        return new CancelPaymentCommand(orderId, idempotencyKey + "-" + orderId, amount, REASON);
    }

    private static void repeat(int times, Runnable scenario) {
        for (int i = 0; i < times; i++) {
            scenario.run();
        }
    }

    private static Instant afterWaiting() {
        return Instant.now().plus(Duration.ofSeconds(11));
    }

    private void awaitCardCompany(String orderId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId), Predicate.isEqual(false));
    }

    private void blockCancelRecord() {
        jdbcTemplate.execute("""
                create trigger block_cancel_record_update before update on payment_cancel for each row
                begin
                    if new.status = 'DONE' then
                        signal sqlstate '45000' set message_text = 'cancel record blocked for test';
                    end if;
                end""");
        jdbcTemplate.execute("""
                create trigger block_cancel_record_insert before insert on payment_cancel for each row
                begin
                    if new.status = 'DONE' then
                        signal sqlstate '45000' set message_text = 'cancel record blocked for test';
                    end if;
                end""");
    }

    private void unblockCancelRecord() {
        jdbcTemplate.execute("drop trigger if exists block_cancel_record_update");
        jdbcTemplate.execute("drop trigger if exists block_cancel_record_insert");
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
