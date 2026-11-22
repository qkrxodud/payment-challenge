package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 12장: 카드사(PG) 장애 동안의 승인 규칙. 카드사가 답하지 않는 동안 승인 요청은 하나하나 읽기 시간 제한까지 기다리고, 기다린 결제는 결과 모름으로 남는다.
 * 지켜야 할 것: 실패가 기준을 넘으면 정한 시간 동안 카드사에 승인을 요청하지 않고 바로 실패시킨다. 승인 요청을 보내지 않았으니 그 결제는
 * 결과 모름이 되지 않는다. 카드사가 거절한 승인은 장애로 세지 않는다. 카드사가 돌아오고 정한 시간이 지나면 다시 승인한다.
 * 장애를 다루는 방법(차단기, 시간 제한만, 다시 시도)이 구현마다 다르므로 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * 장애: 카드사가 승인 요청을 받고 3초 동안 답하지 않다가 아무것도 남기지 않는다(LOST). PG 읽기 시간 제한은 1초.
 * 차단기 기준(계약 값): 최근 승인 4번 가운데 절반 이상 실패하면 열고, 2초 동안 열어 둔 뒤 한 번 시험 삼아 보낸다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "payment.pg.read-timeout=1s",
                "payment.pg.circuit-breaker.enabled=true",
                "payment.pg.circuit-breaker.failure-rate-threshold=50",
                "payment.pg.circuit-breaker.sliding-window-size=4",
                "payment.pg.circuit-breaker.minimum-number-of-calls=4",
                "payment.pg.circuit-breaker.wait-duration-in-open-state=2s",
                "payment.pg.circuit-breaker.permitted-calls-in-half-open-state=1"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class PgOutageContract {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final Duration OUTAGE_ANSWER = Duration.ofSeconds(3);
    static final Duration OPEN = Duration.ofSeconds(2);
    static final int THRESHOLD_CALLS = 4;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentQueryService paymentQueryService;

    /** 앞 시험이 남긴 실패 기록을 지운다. 기록을 두지 않는 구현은 할 것이 없다 */
    abstract void forgetFailures();

    /** 차단기 상태 지표(0 닫힘, 1 열림, 2 반쯤 열림). 지표가 없는 구현은 비어 있다 */
    abstract Optional<Double> circuitStateMeter();

    @BeforeEach
    void startClean() {
        mockPg.reset();
        forgetFailures();
    }

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
    }

    @Test
    @DisplayName("카드사가 답하지 않는 승인이 기준만큼 쌓이면 그 뒤 승인 요청은 카드사에 보내지 않는다")
    void approvalIsNotSentAfterThreshold() {
        failUntilThreshold();
        String orderId = prepare();

        catchThrowable(() -> confirm(orderId));

        assertThat(mockPg.confirmRequestCount(orderId)).isZero();
    }

    @Test
    @DisplayName("차단기가 열린 동안 승인 요청은 읽기 시간 제한을 기다리지 않고 바로 실패한다")
    void approvalFailsFastWhileOpen() {
        failUntilThreshold();
        String orderId = prepare();

        long started = System.nanoTime();
        catchThrowable(() -> confirm(orderId));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(300));
    }

    @Test
    @DisplayName("차단기가 열린 동안 실패한 결제는 결과 모름으로 남지 않는다")
    void paymentFailedWhileOpenIsNotUnknown() {
        failUntilThreshold();
        String orderId = prepare();

        catchThrowable(() -> confirm(orderId));

        assertThat(statusOf(orderId)).isNotEqualTo(PaymentStatus.APPROVAL_UNKNOWN);
    }

    @Test
    @DisplayName("카드사가 돌아오고 열린 시간이 지나면 새 결제는 다시 승인된다")
    void approvalResumesAfterRecovery() {
        failUntilThreshold();
        mockPg.reset();
        sleep(OPEN.plusMillis(200));
        String orderId = prepare();

        catchThrowable(() -> confirm(orderId));

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("카드사가 거절한 승인은 여러 번 쌓여도 그 뒤 승인 요청을 막지 않는다")
    void declinesDoNotStopApprovals() {
        mockPg.decline();
        IntStream.range(0, THRESHOLD_CALLS).forEach(attempt -> catchThrowable(() -> confirm(prepare())));
        mockPg.reset();
        String orderId = prepare();

        confirm(orderId);

        assertThat(mockPg.confirmRequestCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("차단기가 열리면 운영 지표에 열림으로 보인다")
    void openCircuitIsShownInMetrics() {
        failUntilThreshold();

        assertThat(circuitStateMeter()).contains(1.0);
    }

    // 카드사가 답하지 않는 동안 승인을 기준 횟수만큼 요청한다(하나하나 읽기 시간 제한까지 기다린다)
    void failUntilThreshold() {
        mockPg.loseAfter(OUTAGE_ANSWER);
        IntStream.range(0, THRESHOLD_CALLS).forEach(attempt -> catchThrowable(() -> confirm(prepare())));
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    void confirm(String orderId) {
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
    }

    PaymentStatus statusOf(String orderId) {
        return paymentQueryService.find(orderId).status();
    }

    static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
