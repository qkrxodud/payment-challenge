package com.example.payment.application.payment;

import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 12장: 하루치 대사와 보낸 결과 정리 일정을 켜고(1초마다) 부르지 않아도 도는지 본다 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.daily-reconciliation.enabled=true",
                "payment.daily-reconciliation.cron=* * * * * *",
                "payment.outbox.cleanup.enabled=true",
                "payment.outbox.cleanup.cron=* * * * * *"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("매일 도는 작업의 일정")
class ScheduledDailyReconciliationTest {

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("하루치 대사는 따로 부르지 않아도 정해진 때마다 카드사 거래 내역을 읽는다")
    void dailyReconciliationRunsOnSchedule() {
        int before = mockPg.transactionListCount();

        await().atMost(Duration.ofSeconds(10)).until(() -> mockPg.transactionListCount() > before);
    }

    @Test
    @DisplayName("보존 기간이 지난 보낸 결제 결과는 따로 부르지 않아도 정해진 때마다 지워진다")
    void outboxCleanupRunsOnSchedule() {
        String orderId = "order-" + UUID.randomUUID();
        jdbcTemplate.update("insert into payment_outbox (payment_id, order_id, status, type, occurred_at, sent_at) "
                + "values (1, ?, 'APPROVED', 'PAYMENT_RESULT', now(6), ?)", orderId, Timestamp.from(Instant.now().minus(Duration.ofDays(8))));

        await().atMost(Duration.ofSeconds(10)).until(() -> jdbcTemplate.queryForObject(
                "select count(*) from payment_outbox where order_id = ?", Integer.class, orderId) == 0);
    }
}
