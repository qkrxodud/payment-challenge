package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.payment.FaultInjectingEventSender;
import com.example.payment.KafkaBroker;
import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.OrderServer;
import com.example.payment.OrderServerConfiguration;
import com.example.payment.OtherPaymentServer;
import com.example.payment.SendFaults;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 남긴 결제 결과를 보내는 릴레이의 규칙(9장 결정 3~5). 릴레이를 서버끼리 어떻게 나누는지, 보내기와 보낸 것을 남기는 차례가 구현마다 다르므로
 * 구현마다 이 클래스를 상속해 같은 테스트를 돌린다. 주기 실행은 끄고 시험이 릴레이 주기를 직접 부른다.
 * 서버 두 대: 시험 서버에 같은 MySQL·Mock PG·Kafka 브로커를 쓰는 결제 서버를 한 대 더 띄우고(7장 OtherPaymentServer), 두 서버가 같은 때에
 * 릴레이 주기를 한 번씩 시작한다. 승인 이벤트의 첫 보내기는 1초 붙잡는다(SendFaults) — 한 서버가 그 결과를 보내는 사이 다른 서버도 돈다.
 * 결제 금액은 15000원이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, OrderServerConfiguration.class, FaultInjectingEventSender.class})
abstract class OutboxRelayContract {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    static final Duration ARRIVAL = Duration.ofSeconds(15);
    static final Duration SLOW_SEND = Duration.ofSeconds(1);

    private static ConfigurableApplicationContext otherServer;

    @Autowired
    ApplicationContext thisServer;

    @Autowired
    MySQLContainer mysql;

    @Autowired
    MockPgServer mockPg;

    @Autowired
    OrderServer orderServer;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentCancelService paymentCancelService;

    /** 서버 한 대에서 릴레이 주기를 한 번 돌린다 */
    abstract void relayOn(ApplicationContext server);

    /** 다른 서버에도 올릴 시험 부품 */
    Class<?>[] serverComponents() {
        return new Class<?>[] {FaultInjectingEventSender.class};
    }

    @BeforeEach
    void startOtherServer() {
        if (Objects.isNull(otherServer)) {
            otherServer = OtherPaymentServer.start(mysql, mockPg, List.of(), serverComponents());
        }
        KafkaBroker.link().restore();
        SendFaults.clear();
        // 앞 테스트가 남긴 결과가 이번 주기에 섞이지 않게 한다
        jdbcTemplate.update("delete from payment_outbox");
    }

    @AfterEach
    void clearFaults() {
        SendFaults.clear();
        KafkaBroker.link().restore();
        mockPg.reset();
    }

    @AfterAll
    static void stopOtherServer() {
        if (Objects.nonNull(otherServer)) {
            otherServer.close();
            otherServer = null;
        }
    }

    @Test
    @DisplayName("서버 두 대가 함께 릴레이를 돌아도 같은 결제의 이벤트는 만든 순서대로 도착한다")
    void twoServersKeepOrderOfOnePayment() {
        String orderId = relayedByTwoServers();

        assertThat(eventNumbersOf(orderId)).isSorted();
    }

    @Test
    @DisplayName("서버 두 대가 함께 릴레이를 돌아도 승인 뒤 취소된 결제의 주문은 취소 상태다")
    void twoServersLeaveOrderCanceled() {
        String orderId = relayedByTwoServers();

        assertThat(orderServer.statusOf(orderId)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("서버 두 대가 함께 릴레이를 돌아도 같은 이벤트를 두 번 보내지 않는다")
    void twoServersDoNotSendTwice() {
        String orderId = relayedByTwoServers();

        assertThat(eventNumbersOf(orderId)).doesNotHaveDuplicates();
    }

    // 승인 뒤 전액 취소한 결제의 두 결과를 두 서버가 같은 때 릴레이로 보낸다. 남은 것이 없을 때까지 시험 서버에서 주기를 더 돌린 뒤
    // 두 이벤트가 모두 도착하고 1초 더 기다린다(늦게 보낸 같은 이벤트가 있으면 그것까지)
    String relayedByTwoServers() {
        String orderId = approvedThenCanceled();
        SendFaults.delayFirstSend(orderId, "APPROVED", SLOW_SEND);

        MultiServerContract.onServers(List.of(thisServer, otherServer), this::relayOn);
        relayUntilNothingLeft();

        await().pollDelay(Duration.ofSeconds(1)).atMost(ARRIVAL)
                .until(() -> eventNumbersOf(orderId).stream().distinct().count() == 2);
        return orderId;
    }

    void relayUntilNothingLeft() {
        await().atMost(ARRIVAL).until(() -> {
            relayOn(thisServer);
            return unsentCount() == 0;
        });
    }

    int unsentCount() {
        Integer unsent = jdbcTemplate.queryForObject("select count(*) from payment_outbox where sent_at is null", Integer.class);
        return Objects.requireNonNullElse(unsent, 0);
    }

    // 이 주문에 대해 주문 서비스가 받은 이벤트의 고유 번호(남긴 결과의 번호)를 받은 순서대로
    List<Long> eventNumbersOf(String orderId) {
        return orderServer.receivedEventIds(orderId).stream().map(Long::valueOf).toList();
    }

    String approvedThenCanceled() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        paymentCancelService.cancel(new CancelPaymentCommand(orderId, "cancel-" + orderId, null, "Customer changed their mind"));
        return orderId;
    }
}
