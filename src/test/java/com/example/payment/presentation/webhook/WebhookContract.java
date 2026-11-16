package com.example.payment.presentation.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.mockpg.WebhookAttempt;
import com.example.mockpg.WebhookDelivery;
import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.PaymentCancelService;
import com.example.payment.application.payment.PaymentConfirmService;
import com.example.payment.application.payment.PaymentPrepareService;
import com.example.payment.application.payment.PaymentQueryService;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.CancelStatus;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import com.example.payment.presentation.webhook.comparison.WebhookComparisonConfiguration;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * 카드사 웹훅(결제 상태 알림) 처리 규칙(6장). 알림을 무엇으로 믿고(본문, 카드사 조회), 순서가 바뀐 알림과 중복 알림을 어떻게 다루고,
 * 언제 답하는지(받자마자, 반영한 뒤)가 구현마다 다르므로 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * Mock PG 가 실제 HTTP 로 구현마다 다른 주소(webhookPath)에 알림을 보낸다. 200 이 아니면 100ms 간격으로 7번까지 다시 보낸다
 * (토스페이먼츠는 1분부터 4096분까지 늘려 가며 7번).
 * 결제 금액은 15000원, 부분 취소는 5000원이다. PG 읽기 시간 제한은 1초, 늦은 답은 1.5초 뒤에 온다.
 * 보정 대기 시간은 10초다 — "보정 대기 시간 전에"는 그 시간이 지나기 전에 맞춰졌다는 뜻이다.
 * "카드사와 같다"는 결제 상태가 카드사 상태와 짝이 맞고(승인·부분 취소 ↔ 승인·매입, 취소 ↔ 승인 취소·매입 취소, 거절 ↔ 승인 실패),
 * 우리 남은 금액이 카드사 잔액과 같은지로 본다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, WebhookComparisonConfiguration.class})
abstract class WebhookContract {

    static final Duration LATE = Duration.ofMillis(1500);
    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final BigDecimal PART = new BigDecimal("5000");
    static final String REASON = "Customer changed their mind";
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
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentCancelService paymentCancelService;

    @Autowired
    PaymentQueryService paymentQueryService;

    /** 구현마다 Mock PG 가 알림을 보낼 주소 */
    abstract String webhookPath();

    /** 받은 알림을 처리하는 주기 작업을 한 번 돌린다. 요청 안에서 처리하는 구현은 아무것도 하지 않는다 */
    abstract void process();

    @AfterEach
    void resetMockPg() {
        mockPg.resetWebhooks();
        mockPg.reset();
    }

    @Test
    @DisplayName("승인 알림이 승인 응답보다 먼저 와도 결제는 승인 하나로 맞는다")
    void approvalNoticeBeforeAnswerEndsApproved() {
        // given: 카드사가 승인을 기록하고 알림을 보낸 뒤, 승인 답은 붙잡아 둔다
        String orderId = prepare();
        sendWebhooks();
        mockPg.holdConfirmAnswers();
        CompletableFuture<PaymentResult> confirming = CompletableFuture.supplyAsync(
                () -> paymentConfirmService.confirm(confirmRequest(orderId)));
        awaitFirstAnswer(orderId);
        process();

        // when: 알림을 처리한 뒤에 승인 답이 온다
        mockPg.releaseConfirmAnswers();
        PaymentResult answered = confirming.join();

        // then
        assertThat(answered.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("취소 알림이 승인 알림보다 먼저 와도 결제는 카드사와 같아진다")
    void cancelNoticeBeforeApprovalNoticeMatchesCardCompany() {
        // given: 승인 답을 못 받은 결제를 카드사가 승인했고, 그 뒤 가맹점 관리 화면에서 전액 취소했다
        String orderId = prepare();
        sendWebhooks();
        mockPg.holdWebhooks();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> paymentConfirmService.confirm(confirmRequest(orderId)));
        awaitCardCompany(orderId);
        mockPg.reset();
        mockPg.cancelAtCardCompany(orderId);

        // when: 취소 알림이 먼저, 승인 알림이 나중에 온다
        mockPg.releaseWebhooks(true);
        awaitWebhooks();
        process();

        // then
        assertMatchesCardCompany(orderId);
    }

    @Test
    @DisplayName("같은 취소 알림이 두 번 와도 취소 기록은 하나만 생긴다")
    void duplicatedCancelNoticeRecordsOneCancel() {
        String orderId = approvedOrder();
        sendWebhooks();
        mockPg.duplicateWebhooks();

        mockPg.cancelAtCardCompany(orderId, PART);
        awaitWebhooks();
        process();

        assertThat(paymentQueryService.findCancels(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("같은 상태의 알림이 다른 때에 다시 와도 취소 기록은 하나만 생긴다")
    void sameStatusNoticeLaterRecordsOneCancel() {
        String orderId = approvedOrder();
        sendWebhooks();
        mockPg.cancelAtCardCompany(orderId, PART);
        awaitWebhooks();
        process();

        mockPg.resendWebhook(orderId);
        awaitWebhooks();
        process();

        assertThat(paymentQueryService.findCancels(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("같은 알림이 두 번 와도 두 번 모두 받았다고 답한다")
    void duplicatedNoticeIsAnsweredOkBothTimes() {
        String orderId = approvedOrder();
        sendWebhooks();
        mockPg.duplicateWebhooks();

        mockPg.resendWebhook(orderId);
        awaitWebhooks();

        assertThat(firstAnswersOf(orderId)).containsExactly(200, 200);
    }

    @Test
    @DisplayName("카드사 기록과 다른 취소 알림이 와도 결제를 취소하지 않는다")
    void forgedCancelNoticeIsNotApplied() {
        String orderId = approvedOrder();
        sendWebhooks();

        mockPg.forgeWebhook(orderId, "CANCELED", PREPARED);
        awaitWebhooks();
        process();

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("카드사에 승인이 없는 결과 모름 결제에 승인 알림이 와도 승인으로 바꾸지 않는다")
    void forgedApprovalNoticeIsNotApplied() {
        // given: 승인 요청이 카드사에 닿지 않아 카드사에는 기록이 없는 결과 모름 결제
        String orderId = prepare();
        mockPg.loseAfter(LATE);
        catchThrowable(() -> paymentConfirmService.confirm(confirmRequest(orderId)));
        awaitCardCompany(orderId);
        mockPg.reset();
        sendWebhooks();

        // when
        mockPg.forgeWebhook(orderId, "DONE", PREPARED);
        awaitWebhooks();
        process();

        // then
        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVAL_UNKNOWN);
    }

    @Test
    @DisplayName("모르는 주문의 알림에도 받았다고 답한다")
    void unknownOrderNoticeIsAnsweredOk() {
        String orderId = newOrderId();
        sendWebhooks();

        mockPg.forgeWebhook(orderId, "DONE", PREPARED);
        awaitWebhooks();

        assertThat(firstAnswersOf(orderId)).containsExactly(200);
    }

    @Test
    @DisplayName("모르는 주문의 알림으로 결제를 만들지 않는다")
    void unknownOrderNoticeCreatesNoPayment() {
        String orderId = newOrderId();
        sendWebhooks();

        mockPg.forgeWebhook(orderId, "DONE", PREPARED);
        awaitWebhooks();
        process();

        assertThat(catchThrowable(() -> paymentQueryService.find(orderId))).isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("카드사 조회가 늦어도 알림에는 10초 안에 받았다고 답한다")
    void slowLookupStillAnsweredWithinTenSeconds() {
        // given: 카드사 조회가 PG 읽기 시간 제한(1초)보다 늦게 답한다
        String orderId = approvedOrder();
        sendWebhooks();
        mockPg.answerFollowUpsAfter(LATE);

        // when
        mockPg.cancelAtCardCompany(orderId, PART);
        WebhookAttempt first = awaitFirstAnswer(orderId);

        // then
        assertThat(first.getStatusCode()).isEqualTo(200);
        assertThat(first.getElapsedMillis()).isLessThan(10_000);
    }

    @Test
    @DisplayName("결과 모름 결제에 승인 알림이 오면 보정 대기 시간 전에 승인으로 맞춰진다")
    void approvalUnknownIsSettledBeforeWaitTime() {
        String orderId = prepare();
        sendWebhooks();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> paymentConfirmService.confirm(confirmRequest(orderId)));
        awaitCardCompany(orderId);
        mockPg.reset();

        awaitWebhooks();
        process();

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("결과 모름 취소에 알림이 오면 보정 대기 시간 전에 남은 금액이 카드사 잔액과 같아진다")
    void unknownCancelMatchesBalanceBeforeWaitTime() {
        String orderId = unknownCancelOrder();

        awaitWebhooks();
        process();

        assertThat(balanceOf(orderId)).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    @Test
    @DisplayName("결과 모름 취소에 알림이 오면 보정 대기 시간 전에 그 취소가 결과 모름에서 벗어난다")
    void unknownCancelIsClosedBeforeWaitTime() {
        String orderId = unknownCancelOrder();

        awaitWebhooks();
        process();

        assertThat(paymentQueryService.findCancels(orderId)).noneMatch(cancel -> cancel.cancelStatus().isAwaitingResult());
    }

    @Test
    @DisplayName("결과 모름 취소에 알림이 와도 카드사 취소를 따로 한 번 더 기록하지 않는다")
    void unknownCancelNoticeAddsNoOtherCancel() {
        String orderId = unknownCancelOrder();

        awaitWebhooks();
        process();

        assertThat(paymentQueryService.findCancels(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("카드사에서만 일어난 부분 취소 알림이 오면 남은 금액이 카드사 잔액과 같아진다")
    void gatewayOnlyPartialCancelMatchesBalance() {
        String orderId = approvedOrder();
        sendWebhooks();

        mockPg.cancelAtCardCompany(orderId, PART);
        awaitWebhooks();
        process();

        assertThat(balanceOf(orderId)).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    void sendWebhooks() {
        mockPg.sendWebhooksTo("http://localhost:" + port + webhookPath());
    }

    static String newOrderId() {
        return "order-" + UUID.randomUUID();
    }

    String prepare() {
        String orderId = newOrderId();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    // 웹훅을 켜기 전에 승인해 둔 결제
    String approvedOrder() {
        String orderId = prepare();
        paymentConfirmService.confirm(confirmRequest(orderId));
        return orderId;
    }

    // 승인된 결제에 우리가 보낸 부분 취소가 시간 제한을 넘겨 결과 모름으로 남았고, 카드사는 그 뒤에 취소를 마치고 알림을 보냈다
    String unknownCancelOrder() {
        String orderId = approvedOrder();
        sendWebhooks();
        mockPg.answerFollowUpsAfter(LATE);
        catchThrowable(() -> paymentCancelService.cancel(
                new CancelPaymentCommand(orderId, "cancel-1-" + orderId, PART, REASON)));
        awaitCardCompany(orderId);
        mockPg.reset();
        assertThat(paymentQueryService.findCancels(orderId).get(0).cancelStatus()).as("알림 전 취소 상태")
                .isEqualTo(CancelStatus.UNKNOWN);
        return orderId;
    }

    static ConfirmPaymentCommand confirmRequest(String orderId) {
        return new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED);
    }

    PaymentStatus statusOf(String orderId) {
        return paymentQueryService.find(orderId).status();
    }

    // 우리 남은 금액: 결제 금액에서 끝난 취소를 뺀 값
    BigDecimal balanceOf(String orderId) {
        BigDecimal canceled = paymentQueryService.findCancels(orderId).stream()
                .filter(cancel -> cancel.cancelStatus().isDone())
                .map(CancelResult::cancelAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return PREPARED.subtract(canceled);
    }

    void assertMatchesCardCompany(String orderId) {
        String cardCompany = mockPg.paymentState(orderId);
        assertThat(PAIRS.getOrDefault(cardCompany, Set.of())).as("카드사 상태 %s 와 짝", cardCompany).contains(statusOf(orderId));
        assertThat(balanceOf(orderId)).as("카드사 잔액").isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    // 알림마다 처음 보냈을 때 받은 상태 코드
    List<Integer> firstAnswersOf(String orderId) {
        return mockPg.webhookDeliveries(orderId).stream()
                .map(delivery -> delivery.getAttempts().get(0).getStatusCode())
                .toList();
    }

    // 이 주문의 첫 알림을 처음 보냈을 때의 답
    WebhookAttempt awaitFirstAnswer(String orderId) {
        await().atMost(Duration.ofSeconds(15)).until(() -> hasFirstAnswer(orderId));
        return mockPg.webhookDeliveries(orderId).get(0).getAttempts().get(0);
    }

    private boolean hasFirstAnswer(String orderId) {
        return mockPg.webhookDeliveries(orderId).stream()
                .findFirst()
                .map(WebhookDelivery::getAttempts)
                .map(attempts -> attempts.size() > 0)
                .orElse(false);
    }

    // 보낸 알림이 모두 200 을 받았거나 재전송을 다 쓸 때까지 기다린다
    void awaitWebhooks() {
        await().atMost(Duration.ofSeconds(20)).until(mockPg::isWebhookSettled);
    }

    // 결제 서버가 기다리기를 그만둔 뒤에도 카드사는 처리를 이어 간다. 그 처리가 끝날 때까지 기다린다
    void awaitCardCompany(String orderId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId), Predicate.isEqual(false));
    }
}
