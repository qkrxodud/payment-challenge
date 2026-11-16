package com.example.payment.presentation.webhook;

import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.PaymentConfirmService;
import com.example.payment.application.payment.PaymentPrepareService;
import com.example.payment.application.payment.PaymentQueryService;
import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** 웹훅 처리 주기를 켜고(0.2초마다) 처리를 부르지 않아도 받은 알림으로 결제가 맞춰지는지 본다 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "payment.webhook.enabled=true",
                "payment.webhook.interval=200ms"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("받은 웹훅 주기 처리")
class ScheduledWebhookProcessingTest {

    private static final BigDecimal PREPARED = new BigDecimal("15000");

    @LocalServerPort
    int port;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentQueryService paymentQueryService;

    @AfterEach
    void resetMockPg() {
        mockPg.resetWebhooks();
        mockPg.reset();
    }

    @Test
    @DisplayName("카드사에서만 일어난 부분 취소는 따로 부르지 않아도 정해진 주기마다 처리되어 카드사 잔액과 맞춰진다")
    void gatewayOnlyCancelIsFollowedOnSchedule() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        mockPg.sendWebhooksTo("http://localhost:" + port + "/api/v1/webhooks/tosspayments");

        mockPg.cancelAtCardCompany(orderId, new BigDecimal("5000"));

        await().atMost(Duration.ofSeconds(10)).until(() -> balanceOf(orderId).compareTo(mockPg.balanceAmount(orderId)) == 0);
    }

    private BigDecimal balanceOf(String orderId) {
        return PREPARED.subtract(paymentQueryService.findCancels(orderId).stream()
                .filter(cancel -> cancel.cancelStatus().isDone())
                .map(CancelResult::cancelAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
