package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.comparison.ImmediateLookupConfirmService;
import com.example.payment.application.payment.comparison.NetCancelReconciler;
import com.example.payment.application.payment.comparison.RetryRecordConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 4장 측정: 구현마다 같은 결제 묶음을 보내고 보정한 뒤, 카드사 쪽 승인·취소 건수와 결제 최종 상태,
 * 결과 모름에서 확정까지 걸린 보정 횟수와 시간을 남긴다. 기본 빌드에서 빼고 ./gradlew reconciliationMeasurement 로 돌린다.
 * 결제 묶음(12건): 카드사가 1.5초 뒤 승인 4건, 1.5초 뒤 거절 3건, 승인 요청이 닿지 않음 3건, 카드사 승인 뒤 결과 기록 실패 2건.
 * 승인 읽기 시간 제한은 1초다. 두 가지 경우를 본다: 보정 때 카드사가 바로 답함 / 첫 보정의 확인 요청에만 1.5초 늦게 답함.
 * 결과는 build/measurement/ch04-reconciliation.txt 에 덧붙인다.
 */
@Tag("measurement")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class,
        NetCancelReconciler.class, ImmediateLookupConfirmService.class, RetryRecordConfirmService.class})
@DisplayName("결과 모름 보정 측정")
class ReconciliationMeasurement {

    private static final Duration LATE = Duration.ofMillis(1500);
    private static final BigDecimal PREPARED = new BigDecimal("15000");
    private static final int MAX_ROUNDS = 5;
    private static final Path OUTPUT = Path.of("build", "measurement", "ch04-reconciliation.txt");

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentQueryService paymentQueryService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentReconciler paymentReconciler;

    @Autowired
    NetCancelReconciler netCancelReconciler;

    @Autowired
    ImmediateLookupConfirmService immediateLookupConfirmService;

    @Autowired
    RetryRecordConfirmService retryRecordConfirmService;

    @BeforeEach
    void clearPayments() {
        // 5장부터 취소 기록이 결제를 가리키므로 먼저 지운다
        jdbcTemplate.update("delete from payment_cancel");
        // 8장부터 결제에 담긴 상품도 결제를 가리킨다
        jdbcTemplate.update("delete from payment_item");
        jdbcTemplate.update("delete from payment");
    }

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
        jdbcTemplate.execute("drop trigger if exists block_approval_record");
    }

    @Test
    @DisplayName("운영 구현: 카드사 조회 결과대로 닫는다")
    void lookup() {
        measureBoth("운영 구현(조회 보정)", paymentConfirmService::confirm, () -> paymentReconciler.reconcile(afterWaiting()));
    }

    @Test
    @DisplayName("비교 A: 카드사에 취소를 보내고 승인 실패로 닫는다")
    void netCancel() {
        measureBoth("비교 A 망취소", paymentConfirmService::confirm, () -> netCancelReconciler.reconcile(afterWaiting()));
    }

    @Test
    @DisplayName("비교 B: 아무것도 하지 않는다")
    void doNothing() {
        measureBoth("비교 B 아무것도 하지 않음", paymentConfirmService::confirm, () -> { });
    }

    @Test
    @DisplayName("비교 C: 시간 초과 직후 요청 안에서 바로 조회한다")
    void immediateLookup() {
        measureBoth("비교 C 요청 안에서 바로 조회", immediateLookupConfirmService::confirm, () -> { });
    }

    @Test
    @DisplayName("비교 D: 기록 실패를 요청 안에서 다시 시도하고 결과 모름만 보정한다")
    void retryRecord() {
        measureBoth("비교 D 기록 재시도 + 결과 모름만 보정", retryRecordConfirmService::confirm,
                () -> retryRecordConfirmService.reconcile(afterWaiting()));
    }

    private void measureBoth(String name, Function<ConfirmPaymentCommand, PaymentResult> confirm, Runnable reconcile) {
        measure(name + " / 보정 때 카드사가 바로 답함", confirm, reconcile, false);
        clearPayments();
        resetMockPg();
        measure(name + " / 첫 보정의 확인 요청에 늦게 답함", confirm, reconcile, true);
    }

    private void measure(String name, Function<ConfirmPaymentCommand, PaymentResult> confirm, Runnable reconcile,
                         boolean slowFirstRound) {
        List<String> orders = new ArrayList<>();
        sendBatch(orders, confirm);
        orders.forEach(this::awaitCardCompany);
        Map<String, Integer> afterConfirm = countStatuses(orders);

        Instant started = Instant.now();
        int rounds = 0;
        while (rounds < MAX_ROUNDS && hasAwaiting(orders)) {
            rounds++;
            slowFollowUpsIf(slowFirstRound && rounds == 1);
            reconcile.run();
            mockPg.reset();
            // 늦게 답한 취소 요청이 카드사에서 마저 처리될 때까지 기다린다
            orders.forEach(this::awaitCardCompany);
            sleepIf(slowFirstRound && rounds == 1, LATE);
        }
        Duration took = Duration.between(started, Instant.now());

        write(report(name, orders, afterConfirm, rounds, took));
    }

    private void sendBatch(List<String> orders, Function<ConfirmPaymentCommand, PaymentResult> confirm) {
        mockPg.respondAfter(LATE);
        confirmEach(orders, 4, confirm);
        mockPg.declineAfter(LATE);
        confirmEach(orders, 3, confirm);
        mockPg.loseAfter(LATE);
        confirmEach(orders, 3, confirm);
        mockPg.reset();
        blockApprovalRecord();
        confirmEach(orders, 2, confirm);
        jdbcTemplate.execute("drop trigger if exists block_approval_record");
    }

    private void confirmEach(List<String> orders, int count, Function<ConfirmPaymentCommand, PaymentResult> confirm) {
        for (int i = 0; i < count; i++) {
            String orderId = "order-" + UUID.randomUUID();
            paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
            catchThrowable(() -> confirm.apply(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED)));
            orders.add(orderId);
        }
    }

    private String report(String name, List<String> orders, Map<String, Integer> afterConfirm, int rounds, Duration took) {
        Map<String, Integer> cardCompany = new TreeMap<>();
        orders.forEach(orderId -> cardCompany.merge(mockPg.paymentState(orderId), 1, Integer::sum));
        int approvals = orders.stream().mapToInt(mockPg::approvalCount).sum();
        int cancels = orders.stream().mapToInt(mockPg::cancelCount).sum();
        long mismatched = orders.stream().filter(this::isMismatched).count();
        long kept = orders.stream().filter(this::isApprovedOnBothSides).count();
        return String.join("\n",
                "## " + name,
                "결제 " + orders.size() + "건",
                "카드사 승인 처리 " + approvals + "건, 카드사 취소 " + cancels + "건",
                "카드사 최종 상태 " + cardCompany,
                "승인 응답 직후 결제 상태 " + afterConfirm,
                "보정 뒤 결제 상태 " + countStatuses(orders),
                "보정 뒤에도 승인 결과를 남기지 못한 결제(인증·결과 모름) " + orders.stream().filter(this::isAwaiting).count() + "건",
                "카드사와 어긋난 결제 " + mismatched + "건",
                "카드사 승인이 남고 결제도 승인인 건(매출로 남는 결제) " + kept + "건",
                "보정 횟수 " + rounds + "번, 첫 보정부터 끝까지 " + took.toMillis() + "ms",
                "");
    }

    private Map<String, Integer> countStatuses(List<String> orders) {
        Map<String, Integer> counts = new TreeMap<>();
        orders.forEach(orderId -> counts.merge(statusOf(orderId).name(), 1, Integer::sum));
        return counts;
    }

    private boolean hasAwaiting(List<String> orders) {
        return orders.stream().anyMatch(this::isAwaiting);
    }

    private boolean isAwaiting(String orderId) {
        return statusOf(orderId).isAwaitingApprovalResult();
    }

    // 보정 계약과 같은 기준으로 짝을 본다(6장부터 승인 뒤 취소의 짝은 승인 취소)
    private boolean isMismatched(String orderId) {
        return statusOf(orderId) != ReconciliationContract.pairOf(mockPg.paymentState(orderId));
    }

    private boolean isApprovedOnBothSides(String orderId) {
        return "DONE".equals(mockPg.paymentState(orderId)) && statusOf(orderId) == PaymentStatus.APPROVED;
    }

    private PaymentStatus statusOf(String orderId) {
        return paymentQueryService.find(orderId).status();
    }

    private void awaitCardCompany(String orderId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId), Predicate.isEqual(false));
    }

    private void slowFollowUpsIf(boolean slow) {
        if (slow) {
            mockPg.respondAfter(Duration.ZERO, LATE);
        }
    }

    private static void sleepIf(boolean condition, Duration duration) {
        if (condition) {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Instant afterWaiting() {
        return Instant.now().plus(Duration.ofSeconds(11));
    }

    private void blockApprovalRecord() {
        jdbcTemplate.execute("""
                create trigger block_approval_record before update on payment for each row
                begin
                    if new.status = 'APPROVED' then
                        signal sqlstate '45000' set message_text = 'approval record blocked for test';
                    end if;
                end""");
    }

    private static void write(String text) {
        System.out.println(text);
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, text + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
