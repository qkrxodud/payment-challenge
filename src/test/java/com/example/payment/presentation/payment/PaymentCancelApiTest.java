package com.example.payment.presentation.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

/** 5장: 실제 HTTP 로 부르는 결제 취소 API. 결제 금액은 15000원이다 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "payment.pg.read-timeout=500ms")
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("결제 취소 API")
class PaymentCancelApiTest {

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
    @DisplayName("승인된 결제를 부분 취소하면 취소한 금액과 남은 금액으로 답한다")
    void partialCancel() {
        String orderId = approvedOrder();

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-1", "5000");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.field("cancelStatus")).isEqualTo("DONE");
        assertThat(answer.decimal("balanceAmount")).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("금액 없이 취소하면 남은 금액을 모두 취소한다")
    void cancelWithoutAmountCancelsBalance() {
        String orderId = approvedOrder();
        api.cancel(orderId, "cancel-1", "5000");

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-2", "null");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.decimal("cancelAmount")).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Idempotency-Key 없이 온 취소는 받지 않는다")
    void cancelWithoutKey() {
        String orderId = approvedOrder();

        PaymentApi.Answer answer = api.cancel(orderId, null, "5000");

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.field("code")).isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    @DisplayName("300자를 넘는 Idempotency-Key 로 온 취소는 받지 않는다")
    void cancelWithTooLongKey() {
        String orderId = approvedOrder();

        PaymentApi.Answer answer = api.cancel(orderId, "k".repeat(301), "5000");

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.field("code")).isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    @DisplayName("같은 Idempotency-Key 에 다른 금액이 오면 처리할 수 없는 요청이라고 답한다")
    void sameKeyWithOtherAmount() {
        String orderId = approvedOrder();
        api.cancel(orderId, "cancel-1", "5000");

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-1", "3000");

        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.field("code")).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @DisplayName("같은 Idempotency-Key 로 다시 온 취소에는 처음과 같은 답을 준다")
    void sameKeyAgain() {
        String orderId = approvedOrder();
        api.cancel(orderId, "cancel-1", "5000");

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-1", "5000");

        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.decimal("balanceAmount")).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("남은 금액보다 큰 취소는 지금 남은 금액으로는 할 수 없다고 답한다")
    void cancelLargerThanBalance() {
        String orderId = approvedOrder();
        api.cancel(orderId, "cancel-1", "5000");

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-2", "15000");

        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.field("code")).isEqualTo("CANCEL_AMOUNT_EXCEEDS_BALANCE");
    }

    @Test
    @DisplayName("승인 전 결제의 취소는 지금 상태에서는 할 수 없다고 답한다")
    void cancelBeforeApproval() {
        String orderId = newOrderId();
        api.prepare(orderId, 15000);

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-1", "5000");

        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.field("code")).isEqualTo("INVALID_PAYMENT_TRANSITION");
    }

    @Test
    @DisplayName("0원 이하의 취소 금액은 올바르지 않다고 답한다")
    void cancelWithZeroAmount() {
        String orderId = approvedOrder();

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-1", "0");

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.field("code")).isEqualTo("INVALID_AMOUNT");
    }

    @Test
    @DisplayName("취소 사유 없이 온 취소는 받지 않는다")
    void cancelWithoutReason() {
        String orderId = approvedOrder();

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-1", "5000", "\" \"");

        assertThat(answer.status()).isEqualTo(400);
        assertThat(answer.field("code")).isEqualTo("INVALID_CANCEL_REASON");
    }

    @Test
    @DisplayName("없는 주문의 결제를 취소하려 하면 찾을 수 없다고 답한다")
    void cancelUnknownOrder() {
        PaymentApi.Answer answer = api.cancel(newOrderId(), "cancel-1", "5000");

        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.field("code")).isEqualTo("PAYMENT_NOT_FOUND");
    }

    @Test
    @DisplayName("카드사 취소 답이 정해진 시간 안에 오지 않으면 결과를 알 수 없다고 답한다")
    void lateCancelAnswer() {
        String orderId = approvedOrder();
        mockPg.answerFollowUpsAfter(Duration.ofMillis(1500));

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-1", "5000");

        assertThat(answer.status()).isEqualTo(504);
        assertThat(answer.field("code")).isEqualTo("PAYMENT_GATEWAY_NO_RESPONSE");
        awaitCardCompany(orderId);
    }

    @Test
    @DisplayName("카드사가 아직 앞 취소를 처리하는 중에 같은 Idempotency-Key 로 다시 오면 확인 중이라고 답한다")
    void sameKeyWhileCardCompanyIsCanceling() {
        String orderId = approvedOrder();
        mockPg.answerFollowUpsAfter(Duration.ofMillis(1500));
        api.cancel(orderId, "cancel-1", "5000");

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-1", "5000");

        assertThat(answer.status()).isEqualTo(202);
        awaitCardCompany(orderId);
    }

    @Test
    @DisplayName("카드사가 취소에 서버 오류로 답하면 처리하지 못했다고 답한다")
    void cardCompanyFailedToCancel() {
        String orderId = approvedOrder();
        mockPg.failFollowUps();

        PaymentApi.Answer answer = api.cancel(orderId, "cancel-1", "5000");

        assertThat(answer.status()).isEqualTo(502);
        assertThat(answer.field("code")).isEqualTo("PAYMENT_GATEWAY_FAILED");
    }

    private String approvedOrder() {
        String orderId = newOrderId();
        api.prepare(orderId, 15000);
        api.confirm(orderId, 15000);
        return orderId;
    }

    private void awaitCardCompany(String orderId) {
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId), Predicate.isEqual(false));
    }

    private static String newOrderId() {
        return "order-" + UUID.randomUUID();
    }
}
