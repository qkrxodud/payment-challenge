package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 주기 보정을 켜고(0.2초마다, 대기 시간 2초) 보정을 부르지 않아도 결과 모름 결제가 맞춰지는지 본다 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.reconciliation.enabled=true",
                "payment.reconciliation.interval=200ms",
                "payment.reconciliation.wait-after-request=2s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("결과 모름 주기 보정")
class ScheduledReconciliationTest {

    @Autowired
    MockPgServer mockPg;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentQueryService paymentQueryService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
    }

    @Test
    @DisplayName("결과 모름 결제는 따로 부르지 않아도 정해진 주기마다 카드사 기록에 맞춰진다")
    void unknownPaymentIsReconciledOnSchedule() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, new BigDecimal("15000")));
        mockPg.respondAfter(Duration.ofMillis(1500));

        catchThrowable(() -> paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, new BigDecimal("15000"))));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> paymentQueryService.find(orderId).status() == PaymentStatus.APPROVED);
    }
}
