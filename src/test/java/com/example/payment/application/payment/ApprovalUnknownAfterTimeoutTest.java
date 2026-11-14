package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 이 장에서 해결하지 않는 한계를 기록한다: 시간 제한으로 기다리기를 그만둬도 PG 쪽 처리는 계속된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "payment.pg.read-timeout=1s")
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("카드사 답을 기다리다 그만두면")
class ApprovalUnknownAfterTimeoutTest {

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
    @DisplayName("결제는 인증 상태로 남지만 카드사에는 나중에 승인이 남는다")
    void approvedAfterWeStoppedWaiting() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, new BigDecimal("15000")));
        mockPg.respondAfter(Duration.ofMillis(1500));

        Throwable failure = catchThrowable(() -> paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId)));

        assertThat(failure).isInstanceOf(PaymentGatewayNoResponseException.class);
        assertThat(paymentQueryService.find(orderId).status()).isEqualTo(PaymentStatus.AUTHENTICATED);
        await().atMost(Duration.ofSeconds(3)).until(() -> mockPg.approvalCount(orderId) == 1);
    }
}
