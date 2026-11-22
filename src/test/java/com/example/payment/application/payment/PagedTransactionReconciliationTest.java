package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 12장: 카드사 거래 조회를 한 번에 한 건씩만 받게 줄이고, 거래가 여러 쪽이어도 모두 읽어 맞추는지 본다 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "payment.daily-reconciliation.page-size=1")
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("하루치 대사 — 여러 쪽의 카드사 거래")
class PagedTransactionReconciliationTest {

    @Autowired
    MockPgServer mockPg;

    @Autowired
    DailyReconciler dailyReconciler;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("카드사 거래가 한 쪽보다 많아도 다음 쪽까지 모두 읽어 맞춘다")
    void allPagesAreRead() {
        List<String> keys = IntStream.range(0, 3).mapToObj(number -> approvedOnlyAtCardCompany()).toList();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        dailyReconciler.reconcile(today);

        assertThat(PaymentDailyReconciliationTest.resultsIn(jdbcTemplate, today)).extracting(ReconciliationLine::paymentKey)
                .containsAll(keys);
    }

    String approvedOnlyAtCardCompany() {
        String orderId = "order-" + UUID.randomUUID();
        mockPg.approveAtCardCompany(orderId, "key-" + orderId, new BigDecimal("15000"));
        await().atMost(Duration.ofSeconds(1)).until(mockPg::isWebhookSettled);
        return "key-" + orderId;
    }
}
