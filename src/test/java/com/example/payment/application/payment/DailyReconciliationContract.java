package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.OtherPaymentServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
 * 12장: 하루치 카드사 거래와 우리 결제를 맞춰 보는 대사의 규칙.
 * 카드사가 결제를 바꿨다는 알림(6장 웹훅)이 모두 닿지 않으면 우리 기록은 그대로 남는다 — 4장 보정과 6장 따라가기는 결과 모름·인증 상태와 받은 알림만 본다.
 * 지켜야 할 것: 우리 기록이 카드사보다 뒤처진 결제는 대사 뒤 카드사와 같아진다. 금액이 다르거나 한쪽에만 있거나 우리가 앞선 결제는 고치지 않고
 * 운영자가 확인할 어긋남으로 대사 결과에 남긴다. 같은 날을 다시 대사해도 같은 어긋남은 한 번만 남고, 서버가 여러 대여도 대사는 한 번만 돈다.
 * 대사 방법(카드사 거래 내역으로 맞춤, 결제마다 조회, 기록만, 모두 카드사에 맞춤)이 구현마다 다르므로 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * 카드사 알림은 닿지 않는 주소로 보내 7번 재전송을 모두 실패시킨다. 어긋난 결제는 카드사 쪽(가맹점 관리 화면 취소, 카드사에만 남은 승인)이나
 * 우리 쪽 기록(금액·상태를 직접 고침, 카드사가 모르는 승인 결제를 직접 남김)을 시험이 바꿔 만든다.
 * 대사하는 날은 한국 시각의 오늘이다(시험 결제는 모두 오늘 만든다).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.wait-after-request=10s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class DailyReconciliationContract {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final BigDecimal PART = new BigDecimal("5000");
    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    // 아무도 받지 않는 주소. 카드사 알림은 7번 다시 보내도 모두 닿지 않는다
    static final String NOWHERE = "http://localhost:1/webhooks/payments";
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
    PaymentQueryService paymentQueryService;

    /** 이 서버에서 이날의 대사를 한 번 돌린다. 구현마다 대사 방법이 다르다 */
    abstract void reconcile(LocalDate day);

    /** 이 서버에서 이날의 대사를 한 번 돌린다(서버 두 대 시험) */
    abstract void reconcileOn(ApplicationContext server, LocalDate day);

    /** 이날의 대사 결과. 대사 결과를 남기지 않는 구현은 비어 있다 */
    abstract List<ReconciliationLine> resultsOf(LocalDate day);

    @BeforeEach
    void sendWebhooksNowhere() {
        mockPg.sendWebhooksTo(NOWHERE);
    }

    @AfterEach
    void resetMockPg() {
        mockPg.releaseTransactionAnswers();
        mockPg.reset();
        mockPg.resetWebhooks();
    }

    @AfterAll
    static void stopOtherServer() {
        if (Objects.nonNull(otherServer)) {
            otherServer.close();
            otherServer = null;
        }
    }

    @Test
    @DisplayName("카드사에서만 전액 취소되고 알림이 모두 닿지 않은 승인 결제는 대사 뒤 승인 취소가 된다")
    void paymentCanceledOnlyAtCardCompanyBecomesCanceled() {
        String orderId = approved();
        cancelAtCardCompanyUnnoticed(orderId, null);

        reconcile(today());

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVAL_CANCELED);
    }

    @Test
    @DisplayName("카드사에서만 부분 취소되고 알림이 모두 닿지 않은 승인 결제는 대사 뒤 남은 금액이 카드사 잔액과 같다")
    void paymentPartiallyCanceledOnlyAtCardCompanyKeepsBalance() {
        String orderId = approved();
        cancelAtCardCompanyUnnoticed(orderId, PART);

        reconcile(today());

        assertThat(balanceOf(orderId)).isEqualByComparingTo(mockPg.balanceAmount(orderId));
    }

    @Test
    @DisplayName("대사가 카드사를 따라 맞춘 결제는 맞춘 것으로 대사 결과에 남는다")
    void followedPaymentIsRecordedAsFollowed() {
        String orderId = approved();
        cancelAtCardCompanyUnnoticed(orderId, null);

        reconcile(today());

        assertThat(resultsOfPayment(orderId)).extracting(ReconciliationLine::resolution).containsExactly("FOLLOWED");
    }

    @Test
    @DisplayName("카드사와 금액이 다른 결제는 운영자가 확인할 어긋남으로 대사 결과에 남는다")
    void amountMismatchIsLeftForReview() {
        String orderId = approved();
        changeOurAmount(orderId, new BigDecimal("14000"));

        reconcile(today());

        assertThat(resultsOfPayment(orderId)).singleElement()
                .satisfies(line -> assertThat(line.type()).isEqualTo("AMOUNT_MISMATCH"))
                .satisfies(line -> assertThat(line.needsReview()).isTrue());
    }

    @Test
    @DisplayName("카드사와 금액이 다른 결제는 대사가 우리 금액을 바꾸지 않는다")
    void amountMismatchIsNotChanged() {
        String orderId = approved();
        changeOurAmount(orderId, new BigDecimal("14000"));

        reconcile(today());

        assertThat(ourAmountOf(orderId)).isEqualByComparingTo("14000");
    }

    @Test
    @DisplayName("우리에게만 있는 승인 결제는 운영자가 확인할 어긋남으로 대사 결과에 남는다")
    void ourOnlyPaymentIsLeftForReview() {
        String orderId = approvedOnlyOnOurSide();

        reconcile(today());

        assertThat(resultsOfPayment(orderId)).singleElement()
                .satisfies(line -> assertThat(line.type()).isEqualTo("OURS_ONLY"))
                .satisfies(line -> assertThat(line.needsReview()).isTrue());
    }

    @Test
    @DisplayName("우리에게만 있는 승인 결제는 대사가 상태를 바꾸지 않는다")
    void ourOnlyPaymentKeepsStatus() {
        String orderId = approvedOnlyOnOurSide();

        reconcile(today());

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("카드사에만 있는 결제는 운영자가 확인할 어긋남으로 대사 결과에 남는다")
    void cardCompanyOnlyPaymentIsLeftForReview() {
        String orderId = approvedOnlyAtCardCompany();

        reconcile(today());

        assertThat(resultsOfPayment(orderId)).singleElement()
                .satisfies(line -> assertThat(line.type()).isEqualTo("PG_ONLY"))
                .satisfies(line -> assertThat(line.needsReview()).isTrue());
    }

    @Test
    @DisplayName("카드사에만 있는 결제는 대사가 우리 쪽에 결제를 만들지 않는다")
    void cardCompanyOnlyPaymentIsNotCreated() {
        String orderId = approvedOnlyAtCardCompany();

        reconcile(today());

        assertThat(paymentCountOf(orderId)).isZero();
    }

    @Test
    @DisplayName("우리 쪽만 취소된 결제는 카드사 승인으로 되돌리지 않고 운영자가 확인할 어긋남으로 남는다")
    void paymentAheadOfCardCompanyIsLeftForReview() {
        String orderId = approved();
        jdbcTemplate.update("update payment set status = 'APPROVAL_CANCELED' where order_id = ?", orderId);

        reconcile(today());

        assertThat(resultsOfPayment(orderId)).singleElement()
                .satisfies(line -> assertThat(line.type()).isEqualTo("STATUS_MISMATCH"))
                .satisfies(line -> assertThat(line.needsReview()).isTrue());
    }

    @Test
    @DisplayName("우리 기록과 카드사가 같은 결제는 대사 결과에 남지 않는다")
    void matchingPaymentIsNotRecorded() {
        String orderId = approved();
        paymentCancelService.cancel(new CancelPaymentCommand(orderId, "cancel-" + UUID.randomUUID(), PART, "Customer request"));

        reconcile(today());

        assertThat(resultsOfPayment(orderId)).isEmpty();
    }

    @Test
    @DisplayName("같은 날을 두 번 대사해도 같은 어긋남은 대사 결과에 한 번만 남는다")
    void reconcilingSameDayTwiceRecordsOnce() {
        String orderId = approvedOnlyOnOurSide();

        reconcile(today());
        reconcile(today());

        assertThat(resultsOfPayment(orderId)).hasSize(1);
    }

    @Test
    @DisplayName("두 서버가 같은 때 대사를 시작해도 카드사 거래 내역은 한 번만 읽는다")
    void twoServersReadTransactionsOnce() {
        approved();
        ApplicationContext other = otherServer();
        int before = mockPg.transactionListCount();
        mockPg.holdTransactionAnswers();

        // 이 서버가 카드사 거래 내역의 답을 기다리는 동안 다른 서버도 대사를 시작한다
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> reconcileOn(thisServer, today()));
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.transactionListCount() > before);
        reconcileOn(other, today());
        mockPg.releaseTransactionAnswers();
        first.join();

        assertThat(mockPg.transactionListCount() - before).isEqualTo(1);
    }

    // 결제가 승인되고 카드사도 승인을 남긴다
    String approved() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, keyOf(orderId), PREPARED));
        return orderId;
    }

    // 카드사 쪽에서 취소하고(금액이 없으면 남은 금액 전부), 그 알림이 재전송까지 모두 닿지 않을 때까지 기다린다
    void cancelAtCardCompanyUnnoticed(String orderId, BigDecimal amount) {
        mockPg.cancelAtCardCompany(orderId, amount);
        await().atMost(Duration.ofSeconds(10)).until(mockPg::isWebhookSettled);
    }

    // 카드사가 모르는 승인 결제가 우리 쪽에만 남는다(오늘 인증한 결제)
    String approvedOnlyOnOurSide() {
        String orderId = "order-" + UUID.randomUUID();
        jdbcTemplate.update("insert into payment (order_id, amount, status, payment_key, authenticated_at, point_amount) "
                + "values (?, ?, 'APPROVED', ?, now(6), 0)", orderId, PREPARED, keyOf(orderId));
        return orderId;
    }

    // 우리가 모르는 승인이 카드사에만 남는다
    String approvedOnlyAtCardCompany() {
        String orderId = "order-" + UUID.randomUUID();
        mockPg.approveAtCardCompany(orderId, keyOf(orderId), PREPARED);
        await().atMost(Duration.ofSeconds(10)).until(mockPg::isWebhookSettled);
        return orderId;
    }

    void changeOurAmount(String orderId, BigDecimal amount) {
        jdbcTemplate.update("update payment set amount = ? where order_id = ?", amount, orderId);
    }

    List<ReconciliationLine> resultsOfPayment(String orderId) {
        return resultsOf(today()).stream()
                .filter(line -> line.isOf(keyOf(orderId)))
                .toList();
    }

    synchronized ApplicationContext otherServer() {
        if (Objects.isNull(otherServer)) {
            otherServer = OtherPaymentServer.start(mysql, mockPg, SERVER_PROPERTIES);
        }
        return otherServer;
    }

    static String keyOf(String orderId) {
        return "key-" + orderId;
    }

    static LocalDate today() {
        return LocalDate.now(KOREA);
    }

    PaymentStatus statusOf(String orderId) {
        return paymentQueryService.find(orderId).status();
    }

    BigDecimal ourAmountOf(String orderId) {
        return jdbcTemplate.queryForObject("select amount from payment where order_id = ?", BigDecimal.class, orderId);
    }

    int paymentCountOf(String orderId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from payment where order_id = ?", Integer.class, orderId);
        return Objects.requireNonNullElse(count, 0);
    }

    BigDecimal balanceOf(String orderId) {
        return paymentQueryService.findCancels(orderId).stream()
                .filter(cancel -> cancel.cancelStatus().isDone())
                .map(cancel -> cancel.cancelAmount())
                .reduce(PREPARED, BigDecimal::subtract);
    }
}
