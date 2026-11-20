package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Named.named;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;
import com.example.payment.domain.payment.CancelStatus;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 결제 취소와 부분 취소 규칙(5장). 취소를 어떻게 기록하고(취소마다 한 줄, 기록 없이 상태만),
 * 같은 취소 요청을 무엇으로 가리고(멱등키, 금액·사유, 지금 상태), 카드사를 언제 부르는지(기록 뒤, 기록 전)가 구현마다 다르므로
 * 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * 결제 금액은 15000원, 부분 취소는 5000원이다. PG 읽기 시간 제한은 1초, 늦은 취소는 1.5초 뒤에 답한다.
 * 보정은 카드사가 취소 처리를 마친 뒤, 취소를 요청한 지 보정 대기 시간(10초)이 지난 때로 한 번 돌린다.
 * "카드사와 같다"는 우리가 답한(또는 기록한) 남은 금액이 카드사에 남은 잔액(balanceAmount)과 같은지로 본다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class CancelContract {

    static final Duration LATE = Duration.ofMillis(1500);
    static final Duration WAIT_AFTER_REQUEST = Duration.ofSeconds(10);
    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final BigDecimal PART = new BigDecimal("5000");
    static final String REASON = "Customer changed their mind";

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

    /** 구현마다 취소 진입점을 연결한다 */
    abstract CancelResult cancel(CancelPaymentCommand command);

    /** 취소 요청 뒤 보정 대기 시간이 지난 때에 취소 보정을 한 번 돌린다. 보정이 없는 구현은 아무것도 하지 않는다 */
    abstract void reconcile();

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
        jdbcTemplate.execute("drop trigger if exists block_cancel_record_update");
        jdbcTemplate.execute("drop trigger if exists block_cancel_record_insert");
    }

    @Test
    @DisplayName("승인된 결제를 전액 취소하면 결제는 승인 취소 상태가 된다")
    void fullCancelOfApprovedPaymentBecomesApprovalCanceled() {
        String orderId = approvedOrder();

        cancel(request(orderId, "cancel-1", null));

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("매입된 결제를 전액 취소하면 결제는 매입 취소 상태가 된다")
    void fullCancelOfCapturedPaymentBecomesCaptureCanceled() {
        String orderId = capturedOrder();

        cancel(request(orderId, "cancel-1", null));

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.CAPTURE_CANCELED);
    }

    @Test
    @DisplayName("전액 취소한 결제는 카드사에도 취소로 남는다")
    void fullCancelIsCanceledAtCardCompany() {
        String orderId = approvedOrder();

        cancel(request(orderId, "cancel-1", null));

        assertThat(mockPg.paymentState(orderId)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("부분 취소를 두 번 하면 남은 금액이 카드사 잔액과 같다")
    void twoPartialCancelsLeaveSameBalanceAsCardCompany() {
        String orderId = approvedOrder();
        cancel(request(orderId, "cancel-1", PART));

        CancelResult second = cancel(request(orderId, "cancel-2", PART));

        assertThat(second.balanceAmount()).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    @Test
    @DisplayName("부분 취소 뒤 남은 금액이 있으면 결제는 승인 상태 그대로다")
    void partialCancelKeepsApprovedStatus() {
        String orderId = approvedOrder();

        cancel(request(orderId, "cancel-1", PART));

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("부분 취소로 남은 금액이 0원이 되면 결제는 승인 취소 상태가 된다")
    void partialCancelsToZeroBecomeApprovalCanceled() {
        String orderId = approvedOrder();
        cancel(request(orderId, "cancel-1", PART));

        cancel(request(orderId, "cancel-2", PREPARED.subtract(PART)));

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("남은 금액보다 큰 취소는 받지 않는다")
    void cancelLargerThanBalanceIsRefused() {
        String orderId = approvedOrder();
        cancel(request(orderId, "cancel-1", PART));

        Throwable refused = catchThrowable(() -> cancel(request(orderId, "cancel-2", PREPARED)));

        assertThat(errorCodeOf(refused)).isEqualTo(ErrorCode.CANCEL_AMOUNT_EXCEEDS_BALANCE);
    }

    @Test
    @DisplayName("남은 금액보다 큰 취소는 카드사에 보내지 않는다")
    void cancelLargerThanBalanceIsNotSent() {
        String orderId = approvedOrder();
        cancel(request(orderId, "cancel-1", PART));

        catchThrowable(() -> cancel(request(orderId, "cancel-2", PREPARED)));

        assertThat(mockPg.cancelRequestCount(orderId)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notCancelableStatuses")
    @DisplayName("승인이 남지 않은 결제의 취소는 받지 않는다")
    void cancelOfUnapprovedPaymentIsRefused(PaymentStatus status) {
        String orderId = orderIn(status);

        Throwable refused = catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));

        assertThat(errorCodeOf(refused)).isEqualTo(ErrorCode.INVALID_PAYMENT_TRANSITION);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notCancelableStatuses")
    @DisplayName("승인이 남지 않은 결제의 취소는 카드사에 보내지 않는다")
    void cancelOfUnapprovedPaymentIsNotSent(PaymentStatus status) {
        String orderId = orderIn(status);

        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));

        assertThat(mockPg.cancelRequestCount(orderId)).isZero();
    }

    @Test
    @DisplayName("이미 전액 취소된 결제의 새 취소는 받지 않는다")
    void cancelOfCanceledPaymentIsRefused() {
        String orderId = approvedOrder();
        cancel(request(orderId, "cancel-1", null));

        Throwable refused = catchThrowable(() -> cancel(request(orderId, "cancel-2", PART)));

        assertThat(errorCodeOf(refused)).isEqualTo(ErrorCode.INVALID_PAYMENT_TRANSITION);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 로 다시 온 취소는 카드사에 한 번만 보낸다")
    void sameKeyIsSentOnce() {
        String orderId = approvedOrder();
        cancel(request(orderId, "cancel-1", PART));

        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));

        assertThat(mockPg.cancelRequestCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 로 다시 온 취소는 처음과 같은 답을 받는다")
    void sameKeyGetsSameAnswer() {
        String orderId = approvedOrder();
        CancelResult first = cancel(request(orderId, "cancel-1", PART));

        CancelResult again = cancel(request(orderId, "cancel-1", PART));

        assertThat(again).usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(first);
    }

    @Test
    @DisplayName("같은 Idempotency-Key 에 다른 금액이 오면 받지 않는다")
    void sameKeyWithOtherAmountIsRefused() {
        String orderId = approvedOrder();
        cancel(request(orderId, "cancel-1", PART));

        Throwable refused = catchThrowable(() -> cancel(request(orderId, "cancel-1", new BigDecimal("3000"))));

        assertThat(errorCodeOf(refused)).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    @DisplayName("Idempotency-Key 가 다르면 같은 금액의 부분 취소도 두 번 모두 카드사에서 취소된다")
    void otherKeysWithSameAmountAreBothCanceled() {
        String orderId = approvedOrder();
        cancel(request(orderId, "cancel-1", PART));

        cancel(request(orderId, "cancel-2", PART));

        assertThat(mockPg.cancelCount(orderId)).isEqualTo(2);
    }

    @Test
    @DisplayName("카드사 취소 답이 시간 제한을 넘기면 그 취소는 결과 모름으로 남는다")
    void lateCancelAnswerLeavesUnknown() {
        String orderId = approvedOrder();
        mockPg.answerFollowUpsAfter(LATE);

        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));

        assertThat(lastCancelOf(orderId).cancelStatus()).isEqualTo(CancelStatus.UNKNOWN);
        awaitCardCompany(orderId);
    }

    @Test
    @DisplayName("결과를 모르는 취소는 보정 뒤 남은 금액이 카드사 잔액과 같다")
    void unknownCancelMatchesCardCompanyAfterReconciliation() {
        String orderId = approvedOrder();
        mockPg.answerFollowUpsAfter(LATE);
        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));
        awaitCardCompany(orderId);
        mockPg.reset();

        reconcile();

        assertThat(lastCancelOf(orderId).balanceAmount()).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    @Test
    @DisplayName("카드사 취소 뒤 결과 기록이 실패해도, 보정 뒤 남은 금액이 카드사 잔액과 같다")
    void failedCancelRecordMatchesCardCompanyAfterReconciliation() {
        String orderId = approvedOrder();
        blockCancelRecord();
        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));
        // 기록을 막던 문제가 풀린 뒤에 보정이 돈다
        allowCancelRecord();

        reconcile();

        assertThat(lastCancelOf(orderId).balanceAmount()).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    @Test
    @DisplayName("시간 제한을 넘긴 취소가 같은 Idempotency-Key 로 다시 와도 카드사에서 두 번 취소되지 않는다")
    void retryAfterTimeoutIsNotCanceledTwice() {
        String orderId = approvedOrder();
        mockPg.answerFollowUpsAfter(LATE);
        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));
        awaitCardCompany(orderId);
        mockPg.reset();

        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));

        assertThat(mockPg.cancelCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("시간 제한을 넘긴 취소가 같은 Idempotency-Key 로 다시 오면 카드사 잔액과 같은 남은 금액으로 답한다")
    void retryAfterTimeoutIsAnsweredWithCardCompanyBalance() {
        String orderId = approvedOrder();
        mockPg.answerFollowUpsAfter(LATE);
        catchThrowable(() -> cancel(request(orderId, "cancel-1", PART)));
        awaitCardCompany(orderId);
        mockPg.reset();

        CancelResult again = cancel(request(orderId, "cancel-1", PART));

        assertThat(again.balanceAmount()).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    static Stream<Named<PaymentStatus>> notCancelableStatuses() {
        return Stream.of(
                named("승인 전 결제", PaymentStatus.READY),
                named("승인이 거절된 결제", PaymentStatus.APPROVAL_FAILED),
                named("승인 결과를 모르는 결제", PaymentStatus.APPROVAL_UNKNOWN));
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    String approvedOrder() {
        String orderId = prepare();
        paymentConfirmService.confirm(confirmRequest(orderId));
        return orderId;
    }

    // 매입 API 는 아직 없다. 승인된 결제를 매입 상태로 바꿔 둔다
    String capturedOrder() {
        String orderId = approvedOrder();
        jdbcTemplate.update("update payment set status = 'CAPTURED' where order_id = ?", orderId);
        return orderId;
    }

    String orderIn(PaymentStatus status) {
        String orderId = prepare();
        if (status == PaymentStatus.APPROVAL_FAILED) {
            mockPg.decline();
            paymentConfirmService.confirm(confirmRequest(orderId));
        }
        if (status == PaymentStatus.APPROVAL_UNKNOWN) {
            mockPg.respondAfter(LATE);
            catchThrowable(() -> paymentConfirmService.confirm(confirmRequest(orderId)));
            awaitCardCompany(orderId);
        }
        mockPg.reset();
        assertThat(statusOf(orderId)).as("취소하기 전 결제 상태").isEqualTo(status);
        return orderId;
    }

    static ConfirmPaymentCommand confirmRequest(String orderId) {
        return new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED);
    }

    static CancelPaymentCommand request(String orderId, String idempotencyKey, BigDecimal amount) {
        return new CancelPaymentCommand(orderId, idempotencyKey + "-" + orderId, amount, REASON);
    }

    // 방금 취소를 요청한 결제도 보정 대기 시간이 지난 것으로 보는 때
    static Instant afterWaiting() {
        return Instant.now().plus(WAIT_AFTER_REQUEST).plusSeconds(1);
    }

    PaymentStatus statusOf(String orderId) {
        return paymentQueryService.find(orderId).status();
    }

    // 결제에 남은 마지막 취소. 비교 구현은 Idempotency-Key 대신 다른 값으로 취소를 남기므로 키로 찾지 않는다
    CancelResult lastCancelOf(String orderId) {
        List<CancelResult> cancels = paymentQueryService.findCancels(orderId);
        assertThat(cancels).as("결제에 남은 취소").isNotEmpty();
        return cancels.get(cancels.size() - 1);
    }

    static ErrorCode errorCodeOf(Throwable refused) {
        assertThat(refused).isInstanceOf(PaymentChallengeException.class);
        return ((PaymentChallengeException) refused).getErrorCode();
    }

    // 결제 서버가 기다리기를 그만둔 뒤에도 카드사는 처리를 이어 간다. 그 처리가 끝날 때까지 기다린다
    void awaitCardCompany(String orderId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId), Predicate.isEqual(false));
    }

    // 취소 완료로 바꾸는 기록을 MySQL 트리거로 막아 카드사 취소 뒤 기록 실패를 만든다(2·4장과 같은 방법)
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

    private void allowCancelRecord() {
        jdbcTemplate.execute("drop trigger if exists block_cancel_record_update");
        jdbcTemplate.execute("drop trigger if exists block_cancel_record_insert");
    }
}
