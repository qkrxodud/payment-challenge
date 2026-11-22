package com.example.payment.application.payment;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.comparison.PerPaymentInquiryReconciler;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 12장 측정(./gradlew operationsMeasurement — 기본 check 에서 뺐다). 하루치 대사가 결제 수(1천·1만 건)에 따라 걸리는 시간을 방식마다 잰다.
 * 결제 수마다 우리 결제와 카드사 결제를 새로 만든다: 오늘 인증한 승인 결제 N건을 우리 쪽에 남기고(JDBC 한꺼번에), 같은 결제를 카드사에 승인으로 남긴 뒤
 * 그 가운데 1%를 카드사에서만 전액 취소한다(알림은 끔). 방식: 운영(결정 1·2 — 거래 내역을 쪽(5000건)마다 읽고 맞춤), 결정 1 비교(결제마다 카드사 단건 조회).
 * 걸린 시간: 대사를 부른 때부터 끝날 때까지. 카드사 거래 조회 횟수(쪽 수), 카드사 결제 조회 횟수(뒤처진 결제를 따라가거나 결제마다 조회), 남긴 어긋남 수.
 * 결과는 build/measurement/ch12-operations.txt 에 덧붙인다.
 */
@Tag("operations-measurement")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
class DailyReconciliationMeasurement {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final int CANCELED_PERCENT = 1;

    @Autowired
    ApplicationContext context;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DailyReconciler dailyReconciler;

    @Test
    @DisplayName("하루치 대사가 결제 1천·1만 건에서 방식마다 걸리는 시간을 남긴다")
    void measureReconciliation() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        for (int size : List.of(1_000, 10_000)) {
            measure("운영 — 거래 내역으로 맞춤", size, today, dailyReconciler::reconcile);
            measure("결정 1 비교 — 결제마다 단건 조회", size, today, PerPaymentInquiryReconciler.on(context)::reconcile);
        }
    }

    private void measure(String method, int size, LocalDate today, Consumer<LocalDate> reconcile) {
        List<String> orders = freshPayments(size);
        int pagesBefore = mockPg.transactionListCount();
        long started = System.nanoTime();
        reconcile.accept(today);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        PgOutageMeasurement.write("대사 | " + method + " | 결제 " + size + "건(카드사에서만 취소 " + size * CANCELED_PERCENT / 100 + "건) | "
                + elapsed + "ms | 카드사 거래 조회 " + (mockPg.transactionListCount() - pagesBefore) + "번 | 카드사 결제 조회 "
                + orders.stream().mapToInt(mockPg::inquiryCount).sum() + "번 | 남긴 어긋남 " + resultCount(today)
                + " | 맞춘 결제 " + canceledCount() + "건");
    }

    // 앞 측정의 결제를 우리 쪽·카드사 쪽 모두 지우고 새로 만든다
    private List<String> freshPayments(int size) {
        jdbcTemplate.update("delete from reconciliation_result");
        jdbcTemplate.update("delete from payment_status_history");
        jdbcTemplate.update("delete from payment_cancel");
        jdbcTemplate.update("delete from payment_item");
        jdbcTemplate.update("delete from payment_outbox");
        jdbcTemplate.update("delete from payment");
        mockPg.forgetPayments();
        List<String> orders = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
        for (int number = 0; number < size; number++) {
            String orderId = "measure-" + UUID.randomUUID();
            orders.add(orderId);
            rows.add(new Object[]{orderId, PREPARED, "key-" + orderId});
            mockPg.approveAtCardCompany(orderId, "key-" + orderId, PREPARED);
        }
        jdbcTemplate.batchUpdate("insert into payment (order_id, amount, status, payment_key, authenticated_at, point_amount) "
                + "values (?, ?, 'APPROVED', ?, now(6), 0)", rows);
        orders.stream().limit((long) size * CANCELED_PERCENT / 100).forEach(mockPg::cancelAtCardCompany);
        return orders;
    }

    private int resultCount(LocalDate day) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from reconciliation_result where business_date = ?", Integer.class,
                Date.valueOf(day));
        return Objects.requireNonNullElse(count, 0);
    }

    private int canceledCount() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from payment where status = 'APPROVAL_CANCELED'", Integer.class);
        return Objects.requireNonNullElse(count, 0);
    }
}
