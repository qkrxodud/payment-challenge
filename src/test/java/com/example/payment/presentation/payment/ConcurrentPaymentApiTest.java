package com.example.payment.presentation.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.SlowWrites;
import com.example.payment.TestcontainersConfiguration;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 7장: 같은 결제에 겹쳐 온 요청에 결제 API 가 어떤 상태 코드로 답하는지 실제 HTTP 로 확인한다.
 * 겹치는 순간은 서비스 계약 테스트와 같은 방법(먼저 보낸 요청의 기록을 붙잡기, 카드사 답 늦추기)으로 고정한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("겹쳐 온 요청 — 결제 API")
class ConcurrentPaymentApiTest {

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    PaymentApi api;

    SlowWrites slowWrites;

    @BeforeEach
    void connect() {
        api = new PaymentApi(jsonMapper, port);
        slowWrites = new SlowWrites(jdbcTemplate);
    }

    @AfterEach
    void reset() {
        slowWrites.release();
        mockPg.reset();
    }

    @Test
    @DisplayName("같은 주문을 같은 금액으로 동시에 두 번 준비하면 나중 준비도 준비 응답을 받는다")
    void laterSamePreparationIsCreated() {
        String orderId = newOrderId();
        slowWrites.holdNewPayment();
        CompletableFuture<PaymentApi.Answer> first = CompletableFuture.supplyAsync(() -> api.prepare(orderId, 15000));
        slowWrites.awaitHeld();

        PaymentApi.Answer later = api.prepare(orderId, 15000);

        assertThat(first.join().status()).as("먼저 보낸 준비의 답").isEqualTo(201);
        assertThat(later.status()).isEqualTo(201);
    }

    @Test
    @DisplayName("카드사 답을 기다리는 결제에 같은 승인 요청을 다시 보내면 확인 중이라고 답한다")
    void sameApprovalWhileWaitingIsAccepted() {
        String orderId = newOrderId();
        api.prepare(orderId, 15000);
        mockPg.respondAfter(Duration.ofMillis(800));
        CompletableFuture<PaymentApi.Answer> first = CompletableFuture.supplyAsync(() -> api.confirm(orderId, 15000));
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId));

        PaymentApi.Answer again = api.confirm(orderId, 15000);

        assertThat(first.join().status()).as("첫 승인의 답").isEqualTo(200);
        assertThat(again.status()).isEqualTo(202);
    }

    private static String newOrderId() {
        return "order-" + UUID.randomUUID();
    }
}
