package com.example.payment.application.payment;

import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 12장: 하루치 대사 일정을 켜고(1초마다) 대사를 부르지 않아도 카드사 거래 내역을 읽는지 본다 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.daily-reconciliation.enabled=true",
                "payment.daily-reconciliation.cron=* * * * * *"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("하루치 대사 일정")
class ScheduledDailyReconciliationTest {

    @Autowired
    MockPgServer mockPg;

    @Test
    @DisplayName("하루치 대사는 따로 부르지 않아도 정해진 때마다 카드사 거래 내역을 읽는다")
    void dailyReconciliationRunsOnSchedule() {
        int before = mockPg.transactionListCount();

        await().atMost(Duration.ofSeconds(10)).until(() -> mockPg.transactionListCount() > before);
    }
}
