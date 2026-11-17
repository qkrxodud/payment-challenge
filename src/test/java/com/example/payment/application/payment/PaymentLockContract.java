package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.Attempt;
import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.SlowWrites;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.application.payment.dto.ReceiveWebhookCommand;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 결제를 바꾸는 기록이 동시에 올 때의 규칙(7장). 승인 앞뒤 기록(2·4장), 취소 기록(5장), 알림 반영(6장)이 같은 결제를 읽고 바꾼다.
 * 겹친 기록을 무엇으로 가리는지(가리지 않기, 결제 줄 잠금, 변경 번호 비교, 상태 조건 비교)가 구현마다 다르므로
 * 구현마다 이 클래스를 상속해 같은 테스트를 돌린다. 진입점은 운영 서비스 그대로이고, 구현은 결제를 읽어 바꾸는 방법만 바꿔 끼운다.
 * 먼저 보낸 요청의 기록 한 건을 1초 붙잡아 두고(SlowWrites), 그 요청이 잠든 것을 확인한 뒤 두 번째 요청을 보낸다.
 * 결제 금액은 15000원이고, 남은 금액을 함께 넘는 취소는 10000원 두 건, 겹쳐 기록되는 부분 취소는 5000원 두 건이다.
 * "카드사와 같다"는 결제 상태가 카드사 상태와 짝이 맞고 우리 남은 금액이 카드사 잔액과 같은지로 본다(6장과 같은 기준).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class PaymentLockContract {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final BigDecimal LARGE_PART = new BigDecimal("10000");
    static final BigDecimal PART = new BigDecimal("5000");
    // 첫 취소에 카드사가 답하기까지. 두 번째 취소가 요청을 남기다 붙잡힌(1초) 사이에 답이 온다
    static final Duration PG_ANSWER = Duration.ofMillis(300);
    static final String REASON = "Customer changed their mind";
    private static final Map<String, Set<PaymentStatus>> PAIRS = Map.of(
            "DONE", Set.of(PaymentStatus.APPROVED, PaymentStatus.CAPTURED),
            "PARTIAL_CANCELED", Set.of(PaymentStatus.APPROVED, PaymentStatus.CAPTURED),
            "CANCELED", Set.of(PaymentStatus.APPROVAL_CANCELED, PaymentStatus.CAPTURE_CANCELED),
            "ABORTED", Set.of(PaymentStatus.APPROVAL_FAILED));

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
    PaymentWebhookProcessor paymentWebhookProcessor;

    @Autowired
    PaymentQueryService paymentQueryService;

    SlowWrites slowWrites;

    @BeforeEach
    void prepareSlowWrites() {
        slowWrites = new SlowWrites(jdbcTemplate);
    }

    @AfterEach
    void releaseWrites() {
        slowWrites.release();
        mockPg.reset();
    }

    @Test
    @DisplayName("같은 결제 키로 승인 두 건이 동시에 와도 카드사에는 승인을 한 번만 요청한다")
    void sameKeyApprovalsTogetherAreSentOnce() {
        String orderId = prepare();

        confirmTogether(orderId, keyOf(orderId), keyOf(orderId));

        assertThat(mockPg.confirmRequestCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 결제 키로 승인 두 건이 동시에 와도 서버 오류로 답하지 않는다")
    void sameKeyApprovalsTogetherGetNoServerError() {
        String orderId = prepare();

        Attempt<PaymentResult> later = confirmTogether(orderId, keyOf(orderId), keyOf(orderId));

        assertThat(later.isServerError()).as("나중 승인의 답: %s", later.failure()).isFalse();
    }

    @Test
    @DisplayName("다른 결제 키로 승인 두 건이 동시에 오면 카드사에는 한 건만 승인된다")
    void otherKeyApprovalsTogetherAreApprovedOnce() {
        String orderId = prepare();

        confirmTogether(orderId, keyOf(orderId), "other-" + keyOf(orderId));

        assertThat(mockPg.approvalCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("남은 금액을 함께 넘는 취소 두 건이 동시에 오면 카드사에는 한 건만 보낸다")
    void cancelsOverBalanceTogetherAreSentOnce() {
        String orderId = approvedOrder();

        cancelTogether(orderId, "cancel-1", "cancel-2");

        assertThat(mockPg.cancelRequestCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("남은 금액을 함께 넘는 취소 두 건이 동시에 오면 나중 취소는 남은 금액보다 크다고 받지 않는다")
    void laterCancelOverBalanceIsRefused() {
        String orderId = approvedOrder();

        Attempt<CancelResult> later = cancelTogether(orderId, "cancel-1", "cancel-2");

        assertThat(later.errorCode()).isEqualTo(ErrorCode.CANCEL_AMOUNT_EXCEEDS_BALANCE);
    }

    @Test
    @DisplayName("남은 금액을 함께 넘는 취소 두 건이 동시에 와도 카드사에서 취소된 금액은 결제 금액을 넘지 않는다")
    void cancelsOverBalanceTogetherStayWithinPaymentAmount() {
        String orderId = approvedOrder();

        cancelTogether(orderId, "cancel-1", "cancel-2");

        assertThat(PREPARED.subtract(mockPg.balanceAmount(orderId))).isLessThanOrEqualTo(PREPARED);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 로 취소 두 건이 동시에 와도 취소 기록은 하나만 남는다")
    void sameKeyCancelsTogetherLeaveOneCancel() {
        String orderId = approvedOrder();

        cancelTogether(orderId, "cancel-1", "cancel-1");

        assertThat(cancelCountOf(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 로 취소 두 건이 동시에 와도 서버 오류로 답하지 않는다")
    void sameKeyCancelsTogetherGetNoServerError() {
        String orderId = approvedOrder();

        Attempt<CancelResult> later = cancelTogether(orderId, "cancel-1", "cancel-1");

        assertThat(later.isServerError()).as("나중 취소의 답: %s", later.failure()).isFalse();
    }

    @Test
    @DisplayName("같은 Idempotency-Key 로 취소 두 건이 동시에 와도 카드사에서는 한 번만 취소된다")
    void sameKeyCancelsTogetherAreCanceledOnce() {
        String orderId = approvedOrder();

        cancelTogether(orderId, "cancel-1", "cancel-1");

        assertThat(mockPg.cancelCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("부분 취소 두 건의 기록이 겹쳐도 보정 없이 우리 남은 금액이 카드사 잔액과 같다")
    void overlappingPartialCancelsMatchBalanceWithoutReconciliation() {
        // given: 먼저 보낸 5000원 취소가 카드사 답을 기다리는 동안 두 번째 5000원 취소가 요청을 남기다 붙잡힌다.
        // 그사이 카드사가 첫 취소에 답해, 첫 취소의 답 기록이 두 번째 취소의 요청 기록과 겹친다
        String orderId = approvedOrder();
        slowWrites.holdNewCancel();
        mockPg.answerFollowUpsAfter(PG_ANSWER);
        CompletableFuture<Attempt<CancelResult>> first = Attempt.start(
                () -> paymentCancelService.cancel(partCancelRequest(orderId, "cancel-1")));
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(5)).until(() -> mockPg.isWaiting(orderId));

        // when
        Attempt<CancelResult> later = Attempt.of(() -> paymentCancelService.cancel(partCancelRequest(orderId, "cancel-2")));
        first.join();

        // then
        assertThat(later.isAnswered()).as("나중 취소의 답: %s", later.failure()).isTrue();
        assertThat(balanceOf(orderId)).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    @Test
    @DisplayName("알림 반영이 끝나기 전에 승인 답 기록이 겹쳐도 결제는 카드사와 같다")
    void approvalRecordOverlappingWebhookMatchesCardCompany() {
        // given: 카드사가 승인을 남기고 답은 붙잡아 둔 사이, 가맹점 관리 화면에서 전액 취소했고 그 알림을 받았다
        String orderId = prepare();
        mockPg.holdConfirmAnswers();
        CompletableFuture<Attempt<PaymentResult>> confirming = Attempt.start(
                () -> paymentConfirmService.confirm(confirmRequest(orderId, keyOf(orderId))));
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.approvalCount(orderId) == 1);
        mockPg.cancelAtCardCompany(orderId);
        paymentWebhookService.receive(new ReceiveWebhookCommand("PAYMENT_STATUS_CHANGED", orderId, "CANCELED",
                "2026-11-17T10:00:00.000000"));
        // 알림 반영(승인 뒤 카드사 취소를 들여와 승인 취소로)을 남기는 기록을 붙잡는다
        slowWrites.holdPaymentChangeTo(PaymentStatus.APPROVAL_CANCELED);
        CompletableFuture<Attempt<Boolean>> processing = Attempt.start(this::processWebhooks);
        slowWrites.awaitHeld();

        // when: 알림 반영이 끝나기 전에 승인 답이 와서 기록한다
        mockPg.releaseConfirmAnswers();
        confirming.join();
        processing.join();

        // then
        assertMatchesCardCompany(orderId);
    }

    // 먼저 보낸 승인이 인증을 남기다 붙잡힌 동안 두 번째 승인을 보낸다. 두 번째 승인의 결과를 돌려준다
    Attempt<PaymentResult> confirmTogether(String orderId, String firstKey, String laterKey) {
        slowWrites.holdPaymentChangeTo(PaymentStatus.AUTHENTICATED);
        CompletableFuture<Attempt<PaymentResult>> first = Attempt.start(
                () -> paymentConfirmService.confirm(confirmRequest(orderId, firstKey)));
        slowWrites.awaitHeld();
        Attempt<PaymentResult> later = Attempt.of(() -> paymentConfirmService.confirm(confirmRequest(orderId, laterKey)));
        first.join();
        return later;
    }

    // 먼저 보낸 10000원 취소가 요청을 남기다 붙잡힌 동안 두 번째 10000원 취소를 보낸다. 두 번째 취소의 결과를 돌려준다
    Attempt<CancelResult> cancelTogether(String orderId, String firstKey, String laterKey) {
        slowWrites.holdNewCancel();
        CompletableFuture<Attempt<CancelResult>> first = Attempt.start(
                () -> paymentCancelService.cancel(cancelRequest(orderId, firstKey)));
        slowWrites.awaitHeld();
        Attempt<CancelResult> later = Attempt.of(() -> paymentCancelService.cancel(cancelRequest(orderId, laterKey)));
        assertThat(first.join().isAnswered()).as("먼저 보낸 취소의 답: %s", first.join().failure()).isTrue();
        return later;
    }

    boolean processWebhooks() {
        paymentWebhookProcessor.process();
        return true;
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    String approvedOrder() {
        String orderId = prepare();
        paymentConfirmService.confirm(confirmRequest(orderId, keyOf(orderId)));
        return orderId;
    }

    static String keyOf(String orderId) {
        return "key-" + orderId;
    }

    static ConfirmPaymentCommand confirmRequest(String orderId, String paymentKey) {
        return new ConfirmPaymentCommand(orderId, paymentKey, PREPARED);
    }

    static CancelPaymentCommand cancelRequest(String orderId, String idempotencyKey) {
        return new CancelPaymentCommand(orderId, idempotencyKey + "-" + orderId, LARGE_PART, REASON);
    }

    static CancelPaymentCommand partCancelRequest(String orderId, String idempotencyKey) {
        return new CancelPaymentCommand(orderId, idempotencyKey + "-" + orderId, PART, REASON);
    }

    int cancelCountOf(String orderId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from payment_cancel c join payment p on p.id = c.payment_id where p.order_id = ?",
                Integer.class, orderId);
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
        PaymentStatus status = paymentQueryService.find(orderId).status();
        assertThat(PAIRS.getOrDefault(cardCompany, Set.of())).as("카드사 상태 %s 와 짝", cardCompany).contains(status);
        assertThat(balanceOf(orderId)).as("카드사 잔액").isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }
}
