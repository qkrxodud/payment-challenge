package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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
 * 남긴 결제 결과를 보내는 릴레이의 규칙(9장 결정 3~5). 결정 5: 적어도 한 번 — 보낸 뒤 보낸 것을 남기기 전에 죽으면 다시 보내 같은 이벤트가
 * 두 번 갈 수 있지만, 잃지는 않는다. 릴레이를 서버끼리 어떻게 나누는지, 보내기와 보낸 것을 남기는 차례가 구현마다 다르므로
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

    /**
     * 릴레이가 이 결제의 승인 결과를 처리하던 중 죽게 한다 — 그 구현의 두 걸음(보내기, 보낸 것 남기기) 사이에서.
     * 보낸 뒤 남기는 구현(운영)은 보낸 것을 남기는 기록을 막는다(트리거). 남긴 뒤 보내는 구현은 보내려던 그 자리에서 죽인다
     */
    void crashBetweenSteps(String orderId) {
        jdbcTemplate.execute("""
                create trigger block_outbox_mark before update on payment_outbox for each row
                begin
                    signal sqlstate '45000' set message_text = 'relay died for test';
                end""");
    }

    void recoverFromCrash() {
        jdbcTemplate.execute("drop trigger if exists block_outbox_mark");
    }

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
        jdbcTemplate.execute("drop trigger if exists block_outbox_mark");
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

    @Test
    @DisplayName("한 결제의 앞 이벤트를 보내지 못하면 그 주기에는 그 결제의 다음 이벤트도 보내지 않는다")
    void laterEventOfFailedPaymentWaits() {
        String orderId = approvedThenCanceled();
        SendFaults.failFirstSend(orderId, "APPROVED");

        relayOn(thisServer);

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2))
                .until(() -> orderServer.receivedCount(orderId, "APPROVAL_CANCELED") == 0);
    }

    @Test
    @DisplayName("한 결제의 이벤트를 보내지 못해도 그 주기에 다른 결제의 이벤트는 보낸다")
    void otherPaymentsAreStillSent() {
        String failingOrderId = approvedThenCanceled();
        String otherOrderId = approvedThenCanceled();
        SendFaults.failFirstSend(failingOrderId, "APPROVED");

        relayOn(thisServer);

        orderServer.awaitReceived(otherOrderId, "APPROVAL_CANCELED", ARRIVAL);
        assertThat(orderServer.receivedStatuses(otherOrderId)).containsExactly("APPROVED", "APPROVAL_CANCELED");
    }

    @Test
    @DisplayName("보내지 못한 결제의 이벤트는 다음 주기에 만든 순서대로 도착한다")
    void failedPaymentIsSentInOrderNextTime() {
        String orderId = approvedThenCanceled();
        SendFaults.failFirstSend(orderId, "APPROVED");
        relayOn(thisServer);

        relayOn(thisServer);

        await().atMost(ARRIVAL).until(() -> orderServer.receivedStatuses(orderId).size() >= 2);
        assertThat(orderServer.receivedStatuses(orderId)).containsExactly("APPROVED", "APPROVAL_CANCELED");
    }

    @Test
    @DisplayName("릴레이가 이벤트 하나를 처리하던 중 죽어도 다시 돌면 그 이벤트는 주문 서비스에 도착한다")
    void eventArrivesAfterRelayDied() {
        String orderId = relayDiedOnceThenRanAgain();

        orderServer.awaitReceived(orderId, "APPROVED", ARRIVAL);
        assertThat(orderServer.receivedCount(orderId, "APPROVED")).isPositive();
    }

    @Test
    @DisplayName("릴레이가 보낸 뒤 보낸 것을 남기기 전에 죽으면 다시 돌 때 같은 이벤트가 한 번 더 간다")
    void eventIsSentAgainAfterRelayDied() {
        String orderId = relayDiedOnceThenRanAgain();

        await().pollDelay(Duration.ofSeconds(1)).atMost(ARRIVAL)
                .until(() -> orderServer.receivedEventIds(orderId).size() >= 2);
        assertThat(orderServer.receivedEventIds(orderId)).hasSize(2).containsOnly(orderServer.receivedEventIds(orderId).get(0));
    }

    // 승인된 결제의 결과를 릴레이가 처리하던 중 죽고(그 주기가 실패로 끝남), 되살아나 한 주기를 더 돈다
    String relayDiedOnceThenRanAgain() {
        String orderId = approvedOrder();
        crashBetweenSteps(orderId);
        catchThrowable(() -> relayOn(thisServer));
        recoverFromCrash();

        relayOn(thisServer);
        return orderId;
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

    String approvedOrder() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        return orderId;
    }

    String approvedThenCanceled() {
        String orderId = approvedOrder();
        paymentCancelService.cancel(new CancelPaymentCommand(orderId, "cancel-" + orderId, null, "Customer changed their mind"));
        return orderId;
    }
}
