package com.example.payment.presentation.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "payment.pg.read-timeout=500ms")
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("결제 API")
class PaymentApiTest {

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    MockPgServer mockPg;

    PaymentApi api;

    @BeforeEach
    void connect() {
        api = new PaymentApi(jsonMapper, port);
    }

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
    }

    @Test
    @DisplayName("결제를 준비하면 준비 상태로 답한다")
    void prepare() {
        PaymentApi.Answer answer = api.prepare(newOrderId(), 15000);

        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.field("status")).isEqualTo("READY");
    }

    @Test
    @DisplayName("준비한 결제를 카드사가 승인하면 승인 상태로 답하고, 다시 조회해도 승인 상태다")
    void confirm() {
        String orderId = newOrderId();
        api.prepare(orderId, 15000);

        PaymentApi.Answer answer = api.confirm(orderId);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.field("status")).isEqualTo("APPROVED");
        assertThat(api.find(orderId).field("status")).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("카드사가 승인을 거절하면 승인 거절 상태로 답한다")
    void declined() {
        String orderId = newOrderId();
        api.prepare(orderId, 15000);
        mockPg.decline();

        PaymentApi.Answer answer = api.confirm(orderId);

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.field("status")).isEqualTo("APPROVAL_FAILED");
    }

    @Test
    @DisplayName("카드사가 정해진 시간 안에 답하지 않으면 결과를 알 수 없다고 답한다")
    void noAnswer() {
        String orderId = newOrderId();
        api.prepare(orderId, 15000);
        mockPg.respondAfter(Duration.ofSeconds(2));

        PaymentApi.Answer answer = api.confirm(orderId);

        assertThat(answer.status()).isEqualTo(504);
        assertThat(answer.field("code")).isEqualTo("PAYMENT_GATEWAY_NO_RESPONSE");
    }

    @Test
    @DisplayName("없는 주문의 결제를 승인하려 하면 찾을 수 없다고 답한다")
    void confirmUnknown() {
        PaymentApi.Answer answer = api.confirm(newOrderId());

        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.field("code")).isEqualTo("PAYMENT_NOT_FOUND");
    }

    @Test
    @DisplayName("없는 주문의 결제를 조회하면 찾을 수 없다고 답한다")
    void findUnknown() {
        PaymentApi.Answer answer = api.find(newOrderId());

        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.field("code")).isEqualTo("PAYMENT_NOT_FOUND");
    }

    @Test
    @DisplayName("이미 승인된 결제를 다시 승인하려 하면 지금 상태에서는 할 수 없다고 답한다")
    void confirmTwice() {
        String orderId = newOrderId();
        api.prepare(orderId, 15000);
        api.confirm(orderId);

        PaymentApi.Answer answer = api.confirm(orderId);

        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.field("code")).isEqualTo("INVALID_PAYMENT_TRANSITION");
    }

    @Test
    @DisplayName("읽을 수 없는 요청 내용은 받지 않는다")
    void unreadable() {
        PaymentApi.Answer answer = api.post("", "{\"orderId\":");

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.field("code")).isEqualTo("INVALID_INPUT");
    }

    private static String newOrderId() {
        return "order-" + UUID.randomUUID();
    }
}
