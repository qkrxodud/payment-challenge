package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.application.payment.dto.ReceiveWebhookCommand;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 12장: 결제 상태 이력의 규칙. 대사에서 어긋난 결제를 보면 그 결제가 언제, 어떤 길(승인 요청, 보정, 알림, 취소, 대사)로 지금 상태가 됐는지 알아야 한다.
 * 지켜야 할 것: 상태가 바뀔 때마다 이력이 한 줄(이전 상태, 다음 상태, 바꾼 행위, 계기, 바뀐 때) 남고, 상태 변경이 되돌려지면 이력도 남지 않는다.
 * 상태가 그대로인 기록(부분 취소)은 이력을 남기지 않는다.
 * 남기는 방법(상태 이력 표, 결제 결과 기록으로 대신함, 남기지 않음)이 구현마다 다르므로 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * PG 읽기 시간 제한 1초, 카드사 늦은 답 1.5초, 보정 대기 시간 10초는 4장 계약과 같다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class StatusHistoryContract {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final Duration LATE = Duration.ofMillis(1500);

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
    PaymentReconciler paymentReconciler;

    @Autowired
    PaymentWebhookService paymentWebhookService;

    @Autowired
    PaymentWebhookProcessor paymentWebhookProcessor;

    /** 이 결제의 상태 이력(바뀐 순서대로). 이력을 남기지 않는 구현은 비어 있다 */
    abstract List<HistoryLine> historyOf(String orderId);

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
        jdbcTemplate.execute("drop trigger if exists block_approval_record");
    }

    @Test
    @DisplayName("승인된 결제에는 인증과 승인이 차례로 한 줄씩 이력에 남는다")
    void approvedPaymentHasAuthenticationAndApproval() {
        String orderId = approved();

        assertThat(historyOf(orderId)).extracting(HistoryLine::transition)
                .containsExactly("READY -> AUTHENTICATED (AUTHENTICATE)", "AUTHENTICATED -> APPROVED (APPROVE)");
    }

    @Test
    @DisplayName("승인을 요청한 결제의 이력은 승인 요청이 바꿨다고 남는다")
    void confirmationIsRecordedAsTrigger() {
        String orderId = approved();

        assertThat(historyOf(orderId)).extracting(HistoryLine::trigger).containsOnly("CONFIRM");
    }

    @Test
    @DisplayName("카드사 답을 받지 못한 결제가 보정으로 승인되면 결과 모름을 거친 이력이 남는다")
    void reconciledPaymentPassesThroughUnknown() {
        String orderId = unknownThenReconciled();

        assertThat(historyOf(orderId)).extracting(HistoryLine::to)
                .containsExactly("AUTHENTICATED", "APPROVAL_UNKNOWN", "APPROVED");
    }

    @Test
    @DisplayName("보정이 바꾼 상태는 보정이 바꿨다고 이력에 남는다")
    void reconciliationIsRecordedAsTrigger() {
        String orderId = unknownThenReconciled();

        assertThat(historyOf(orderId)).last().extracting(HistoryLine::trigger).isEqualTo("APPROVAL_RECONCILIATION");
    }

    @Test
    @DisplayName("카드사 알림으로 따라간 취소는 알림이 바꿨다고 이력에 남는다")
    void webhookCancelIsRecordedAsTrigger() {
        String orderId = approved();
        mockPg.cancelAtCardCompany(orderId);
        paymentWebhookService.receive(new ReceiveWebhookCommand("PAYMENT_STATUS_CHANGED", orderId, "CANCELED", null));

        paymentWebhookProcessor.process();

        assertThat(historyOf(orderId)).last()
                .satisfies(line -> assertThat(line.transition()).isEqualTo("APPROVED -> APPROVAL_CANCELED (CANCEL)"))
                .satisfies(line -> assertThat(line.trigger()).isEqualTo("WEBHOOK"));
    }

    @Test
    @DisplayName("고객이 전액 취소한 결제에는 승인에서 승인 취소로 바뀐 한 줄이 더 남는다")
    void fullCancelAddsOneLine() {
        String orderId = approved();

        cancel(orderId, null);

        assertThat(historyOf(orderId)).last()
                .satisfies(line -> assertThat(line.transition()).isEqualTo("APPROVED -> APPROVAL_CANCELED (CANCEL)"))
                .satisfies(line -> assertThat(line.trigger()).isEqualTo("CANCEL"));
    }

    @Test
    @DisplayName("상태가 그대로인 부분 취소는 이력을 남기지 않는다")
    void partialCancelLeavesNoLine() {
        String orderId = approved();

        cancel(orderId, new BigDecimal("5000"));

        assertThat(historyOf(orderId)).hasSize(2);
    }

    @Test
    @DisplayName("승인 기록이 되돌려지면 승인 이력도 남지 않는다")
    void rolledBackApprovalLeavesNoLine() {
        String orderId = prepare();
        jdbcTemplate.execute("""
                create trigger block_approval_record before update on payment for each row
                begin
                    if new.status = 'APPROVED' then
                        signal sqlstate '45000' set message_text = 'approval record blocked for test';
                    end if;
                end""");

        catchThrowable(() -> confirm(orderId));

        assertThat(historyOf(orderId)).extracting(HistoryLine::to).doesNotContain("APPROVED");
    }

    @Test
    @DisplayName("이력에는 상태를 바꾼 요청을 처리하던 때가 바뀐 때로 남는다")
    void changedAtIsWhenTheRequestWasHandled() {
        String orderId = prepare();
        Instant before = Instant.now();

        confirm(orderId);

        Instant after = Instant.now();
        assertThat(historyOf(orderId)).isNotEmpty()
                .allSatisfy(line -> assertThat(line.changedAt()).isBetween(before.minusMillis(1), after.plusMillis(1)));
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    void confirm(String orderId) {
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
    }

    String approved() {
        String orderId = prepare();
        confirm(orderId);
        return orderId;
    }

    void cancel(String orderId, BigDecimal amount) {
        paymentCancelService.cancel(new CancelPaymentCommand(orderId, "cancel-" + UUID.randomUUID(), amount, "Customer request"));
    }

    // 카드사는 승인했지만 답이 시간 제한을 넘겨 결과 모름으로 남은 결제를, 보정 대기 시간이 지난 때의 보정이 승인으로 닫는다
    String unknownThenReconciled() {
        String orderId = prepare();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> confirm(orderId));
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId), Predicate.isEqual(false));
        mockPg.reset();
        paymentReconciler.reconcile(Instant.now().plusSeconds(11));
        return orderId;
    }
}
