package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.Attempt;
import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 카드사 답을 기다리는(인증 상태) 결제에 승인 요청이 다시 올 때의 규칙(7장, 4장에서 정하지 않고 넘긴 것).
 * 같은 결제 키의 다시 온 요청에 무엇으로 답하는지(지금처럼 할 수 없다고, 확인 중이라고, 첫 요청이 끝날 때까지 기다렸다가 같은 결과로)가
 * 구현마다 다르므로 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * 첫 승인은 카드사가 0.8초 뒤에 답한다. 카드사가 첫 승인을 처리하는 중인 것을 확인한 뒤 두 번째 승인을 보낸다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class InProgressConfirmContract {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final Duration FIRST_ANSWER = Duration.ofMillis(800);

    @Autowired
    MockPgServer mockPg;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    /** 구현마다 승인 진입점을 연결한다 */
    abstract PaymentResult confirm(ConfirmPaymentCommand command);

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
    }

    @Test
    @DisplayName("카드사 답을 기다리는 결제에 같은 결제 키로 승인이 다시 오면 카드사에 다시 요청하지 않는다")
    void sameKeyWhileWaitingIsNotSentAgain() {
        String orderId = prepare();

        confirmWhileWaiting(orderId, keyOf(orderId));

        assertThat(mockPg.confirmRequestCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("카드사 답을 기다리는 결제에 같은 결제 키로 승인이 다시 와도 할 수 없다고 답하지 않는다")
    void sameKeyWhileWaitingIsNotRefused() {
        String orderId = prepare();

        Attempt<PaymentResult> again = confirmWhileWaiting(orderId, keyOf(orderId));

        assertThat(again.errorCode()).as("다시 온 승인의 답: %s", again.failure()).isNull();
    }

    @Test
    @DisplayName("카드사 답을 기다리는 결제에 다른 결제 키로 승인이 오면 지금 상태에서는 할 수 없다고 답한다")
    void otherKeyWhileWaitingIsRefused() {
        String orderId = prepare();

        Attempt<PaymentResult> other = confirmWhileWaiting(orderId, "other-" + keyOf(orderId));

        assertThat(other.errorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_TRANSITION);
    }

    @Test
    @DisplayName("카드사 답을 기다리는 결제에 다른 결제 키로 승인이 와도 카드사에 요청하지 않는다")
    void otherKeyWhileWaitingIsNotSent() {
        String orderId = prepare();

        confirmWhileWaiting(orderId, "other-" + keyOf(orderId));

        assertThat(mockPg.confirmRequestCount(orderId)).isEqualTo(1);
    }

    // 첫 승인을 카드사가 처리하는 동안 이 결제 키로 승인을 다시 보낸다. 다시 보낸 승인의 결과를 돌려준다
    Attempt<PaymentResult> confirmWhileWaiting(String orderId, String paymentKey) {
        mockPg.respondAfter(FIRST_ANSWER);
        CompletableFuture<Attempt<PaymentResult>> first = Attempt.start(() -> confirm(confirmRequest(orderId, keyOf(orderId))));
        await().atMost(Duration.ofSeconds(5)).until(() -> mockPg.isWaiting(orderId));
        Attempt<PaymentResult> again = Attempt.of(() -> confirm(confirmRequest(orderId, paymentKey)));
        assertThat(first.join().isAnswered()).as("첫 승인의 답: %s", first.join().failure()).isTrue();
        return again;
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    static String keyOf(String orderId) {
        return "key-" + orderId;
    }

    static ConfirmPaymentCommand confirmRequest(String orderId, String paymentKey) {
        return new ConfirmPaymentCommand(orderId, paymentKey, PREPARED);
    }
}
