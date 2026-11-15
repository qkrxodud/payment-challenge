package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.PaymentGatewayNoResponseException;
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
 * 결제 승인 규칙. 트랜잭션 경계를 어떻게 잡든(하나로 묶기, 나누기, 나누는 방법) 같은 규칙을 지켜야 하므로
 * 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * DB 연결은 2개, 연결을 기다리는 시간은 250ms(Hikari 최솟값), PG 읽기 시간 제한은 1초로 둔다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.hikari.maximum-pool-size=2",
                "spring.datasource.hikari.connection-timeout=250",
                "payment.pg.read-timeout=1s"
        })
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class PaymentConfirmContract {

    private static final int CONNECTIONS = 2;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentQueryService paymentQueryService;

    /** 구현마다 승인 진입점을 연결한다 */
    abstract PaymentResult confirm(ConfirmPaymentCommand command);

    @AfterEach
    void resetMockPg() {
        mockPg.reset();
    }

    @Test
    @DisplayName("카드사가 승인하면 결제는 승인 상태가 된다")
    void approvedByCardCompany() {
        String orderId = prepare();

        confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, new BigDecimal("15000")));

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("카드사가 승인을 거절하면 결제는 승인 거절 상태로 남는다")
    void declinedByCardCompany() {
        String orderId = prepare();
        mockPg.decline();

        catchThrowable(() -> confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, new BigDecimal("15000"))));

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    @Test
    @DisplayName("카드사 답을 기다리는 승인이 연결 수만큼 있어도 다른 결제를 조회할 수 있다")
    void lookupWhileApprovalsWait() {
        String other = prepare();
        String[] waiting = {prepare(), prepare()};
        mockPg.respondAfter(Duration.ofMillis(800));

        CompletableFuture<?>[] approvals = new CompletableFuture<?>[CONNECTIONS];
        for (int i = 0; i < CONNECTIONS; i++) {
            String orderId = waiting[i];
            approvals[i] = CompletableFuture.runAsync(() -> confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, new BigDecimal("15000"))));
        }
        await().atMost(Duration.ofSeconds(5))
                .until(() -> mockPg.isWaiting(waiting[0]) && mockPg.isWaiting(waiting[1]));

        Throwable lookupFailure = catchThrowable(() -> paymentQueryService.find(other));
        // 다음 테스트가 남은 승인 요청의 영향을 받지 않도록 결과를 보기 전에 모두 끝낸다
        CompletableFuture.allOf(approvals).handle((done, failure) -> done).join();

        assertThat(lookupFailure).isNull();
    }

    @Test
    @DisplayName("카드사 답이 정해진 시간 안에 오지 않으면 기다리지 않고 응답 없음을 알린다")
    void stopWaitingForSlowCardCompany() {
        String orderId = prepare();
        mockPg.respondAfter(Duration.ofSeconds(3));

        long started = System.nanoTime();
        Throwable failure = catchThrowable(() -> confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, new BigDecimal("15000"))));
        Duration waited = Duration.ofNanos(System.nanoTime() - started);

        assertThat(failure).isInstanceOf(PaymentGatewayNoResponseException.class);
        assertThat(waited).isLessThan(Duration.ofSeconds(2));
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, new BigDecimal("15000")));
        return orderId;
    }

    PaymentStatus statusOf(String orderId) {
        return paymentQueryService.find(orderId).status();
    }
}
