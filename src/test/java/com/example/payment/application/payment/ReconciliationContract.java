package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 카드사 답을 받지 못한 결제를 카드사 기록에 맞추는 규칙(4장).
 * 맞추는 방법(조회, 망취소, 아무것도 하지 않음)과 때(주기 보정, 요청 안에서 바로)가 구현마다 다르므로
 * 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * PG 읽기 시간 제한은 1초, 카드사는 1.5초 뒤에 답한다. 보정은 카드사가 처리를 마친 뒤,
 * 승인을 요청한 지 보정 대기 시간(10초)이 지난 때로 한 번 돌린다.
 * "어긋나지 않는다"는 카드사에 남은 최종 상태(승인/취소/거절/기록 없음)와 결제 상태가 짝이 맞는지로 본다:
 * 카드사 승인이면 승인, 승인 뒤 취소면 승인 취소(6장부터, 4장에서는 승인 실패), 거절·기록 없음이면 승인 실패.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class ReconciliationContract {

    static final Duration LATE = Duration.ofMillis(1500);
    static final Duration WAIT_AFTER_REQUEST = Duration.ofSeconds(10);
    private static final BigDecimal PREPARED = new BigDecimal("15000");

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentQueryService paymentQueryService;

    /** 구현마다 승인 진입점을 연결한다 */
    abstract PaymentResult confirm(ConfirmPaymentCommand command);

    /** 승인 요청 뒤 보정 대기 시간이 지난 때에 보정을 한 번 돌린다. 구현마다 보정 방법이 다르다 */
    abstract void reconcile();

    // 앞 테스트가 남긴 결과 모름 결제가 이번 보정 대상에 섞이지 않게 한다
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
    @DisplayName("카드사가 승인했지만 답이 시간 제한을 넘기면, 보정 뒤 결제 기록이 카드사와 어긋나지 않는다")
    void lateApprovalMatchesCardCompanyAfterReconciliation() {
        String orderId = prepare();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> confirm(request(orderId)));
        awaitCardCompany(orderId);

        reconcile();

        assertMatchesCardCompany(orderId);
    }

    @Test
    @DisplayName("카드사 승인 뒤 결과 기록이 실패해도, 보정 뒤 결제 기록이 카드사와 어긋나지 않는다")
    void failedRecordMatchesCardCompanyAfterReconciliation() {
        String orderId = prepare();
        blockApprovalRecord();
        catchThrowable(() -> confirm(request(orderId)));
        // 기록을 막던 문제가 풀린 뒤에 보정이 돈다
        allowApprovalRecord();

        reconcile();

        assertMatchesCardCompany(orderId);
    }

    @Test
    @DisplayName("카드사가 거절했지만 답을 받지 못하면, 보정 뒤 승인 실패로 닫힌다")
    void lateDeclineIsClosedAsFailed() {
        String orderId = prepare();
        mockPg.declineAfter(LATE);
        catchThrowable(() -> confirm(request(orderId)));
        awaitCardCompany(orderId);

        reconcile();

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    @Test
    @DisplayName("승인 요청이 카드사에 닿지 않았으면, 보정 뒤 승인 실패로 닫힌다")
    void lostRequestIsClosedAsFailed() {
        String orderId = prepare();
        mockPg.loseAfter(LATE);
        catchThrowable(() -> confirm(request(orderId)));
        awaitCardCompany(orderId);

        reconcile();

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    @Test
    @DisplayName("보정 중 카드사에 보낸 확인 요청에도 답이 없으면 결제 상태를 바꾸지 않는다")
    void unansweredFollowUpLeavesStatus() {
        String orderId = prepare();
        mockPg.respondAfter(LATE, LATE);
        catchThrowable(() -> confirm(request(orderId)));
        awaitCardCompany(orderId);
        PaymentStatus before = statusOf(orderId);

        reconcile();

        assertThat(statusOf(orderId)).isEqualTo(before);
    }

    @Test
    @DisplayName("확인 요청에 답이 없던 결제는 다음 보정에서 다시 확인해 카드사와 맞춘다")
    void unansweredFollowUpIsRetriedNextTime() {
        String orderId = prepare();
        mockPg.respondAfter(LATE, LATE);
        catchThrowable(() -> confirm(request(orderId)));
        awaitCardCompany(orderId);
        reconcile();
        mockPg.reset();

        reconcile();

        assertMatchesCardCompany(orderId);
    }

    @Test
    @DisplayName("이미 승인된 결제는 보정해도 승인 상태 그대로다")
    void approvedPaymentStaysApproved() {
        String orderId = prepare();
        confirm(request(orderId));

        reconcile();

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("결과를 모르는 결제에 같은 결제 키로 다시 오면 카드사에 다시 승인을 요청하지 않는다")
    void repeatedRequestForUnknownPaymentIsNotSentAgain() {
        String orderId = prepare();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> confirm(request(orderId)));

        catchThrowable(() -> confirm(request(orderId)));

        assertThat(mockPg.confirmRequestCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("결과를 모르는 결제에 같은 결제 키로 다시 오면 확인 중이라고 답한다")
    void repeatedRequestForUnknownPaymentIsAnsweredAsUnknown() {
        String orderId = prepare();
        mockPg.respondAfter(LATE);
        catchThrowable(() -> confirm(request(orderId)));

        PaymentResult again = confirm(request(orderId));

        assertThat(again.status()).isEqualTo(PaymentStatus.APPROVAL_UNKNOWN);
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    static ConfirmPaymentCommand request(String orderId) {
        return new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED);
    }

    // 방금 승인을 요청한 결제도 보정 대기 시간이 지난 것으로 보는 때
    static Instant afterWaiting() {
        return Instant.now().plus(WAIT_AFTER_REQUEST).plusSeconds(1);
    }

    PaymentStatus statusOf(String orderId) {
        return paymentQueryService.find(orderId).status();
    }

    // 결제 서버가 기다리기를 그만둔 뒤에도 카드사는 처리를 이어 간다. 그 처리가 끝날 때까지 기다린다
    void awaitCardCompany(String orderId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId), Predicate.isEqual(false));
    }

    // 카드사 쪽 최종 상태가 승인이면 승인, 승인 뒤 취소면 승인 취소, 거절·기록 없음이면 승인 실패여야 짝이 맞는다.
    // 6장부터 승인 뒤 취소(CANCELED)의 짝을 승인 실패에서 승인 취소로 바꿨다(웹훅과 같은 해석)
    void assertMatchesCardCompany(String orderId) {
        assertThat(statusOf(orderId)).as("카드사 상태 %s 와 짝", mockPg.paymentState(orderId))
                .isEqualTo(pairOf(mockPg.paymentState(orderId)));
    }

    static PaymentStatus pairOf(String cardCompany) {
        if ("DONE".equals(cardCompany)) {
            return PaymentStatus.APPROVED;
        }
        if ("CANCELED".equals(cardCompany)) {
            return PaymentStatus.APPROVAL_CANCELED;
        }
        return PaymentStatus.APPROVAL_FAILED;
    }

    // 승인 상태로 바꾸는 저장을 MySQL 트리거로 막아 카드사 승인 뒤 기록 실패를 만든다(2장과 같은 방법)
    void blockApprovalRecord() {
        jdbcTemplate.execute("""
                create trigger block_approval_record before update on payment for each row
                begin
                    if new.status = 'APPROVED' then
                        signal sqlstate '45000' set message_text = 'approval record blocked for test';
                    end if;
                end""");
    }

    void allowApprovalRecord() {
        jdbcTemplate.execute("drop trigger if exists block_approval_record");
    }
}
