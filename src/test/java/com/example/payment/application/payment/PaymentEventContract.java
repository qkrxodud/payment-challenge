package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import com.example.payment.BrokerLink;
import com.example.payment.KafkaBroker;
import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.OrderServer;
import com.example.payment.OrderServerConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 결제 결과를 주문 서비스에 알리는 규칙(9장). 결제 서버와 주문 서비스(같은 MySQL 의 다른 스키마)가 Kafka 브로커 하나를 사이에 둔다.
 * 이벤트를 언제·어떻게 보내는지(상태를 바꾼 그 자리에서 바로, 커밋 뒤, 남겨 두고 따로 보냄)가 구현마다 다르므로 구현마다 이 클래스를 상속해
 * 같은 테스트를 돌린다. 계약은 주문 서비스에 무엇이 도착했는지로 본다.
 * 브로커를 끊고 늦추는 일은 시험 JVM 과 브로커 사이의 중계(BrokerLink)로 한다. 브로커가 받았다는 답을 기다리는 시간은 3초로 줄였다(KafkaBroker).
 * 결제 금액은 15000원이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, OrderServerConfiguration.class})
abstract class PaymentEventContract {

    static final BigDecimal PREPARED = new BigDecimal("15000");
    // 이벤트가 도착하기를 기다리는 가장 긴 시간
    static final Duration ARRIVAL = Duration.ofSeconds(15);
    // 끊긴 브로커: 받았다는 답을 기다리는 시간(3초)보다 오래 끊는다
    static final Duration CUT = KafkaBroker.DELIVERY_TIMEOUT.plusSeconds(1);
    // 느린 브로커: 답을 1.5초 붙잡는다
    static final Duration SLOW = Duration.ofMillis(1500);

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

    BrokerLink broker = KafkaBroker.link();

    /** 앞 테스트가 남긴 것 가운데 아직 브로커에 보내지 않은 이벤트 수. 바로 보내는 구현은 남겨 두는 것이 없다 */
    int pendingEvents() {
        return 0;
    }

    @BeforeEach
    void awaitPendingEvents() {
        broker.restore();
        await().atMost(ARRIVAL).until(() -> pendingEvents() == 0);
    }

    @AfterEach
    void restore() {
        broker.restore();
        orderServer.releaseHold();
        jdbcTemplate.execute("drop trigger if exists block_approval_record");
        mockPg.reset();
    }

    @Test
    @DisplayName("승인된 결제의 주문은 주문 서비스에서 결제 완료가 된다")
    void approvedPaymentMakesOrderPaid() {
        String orderId = approvedOrder();

        orderServer.awaitReceived(orderId, "APPROVED", ARRIVAL);

        assertThat(orderServer.statusOf(orderId)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("승인이 거절된 결제의 주문은 주문 서비스에서 결제 실패가 된다")
    void declinedPaymentMakesOrderFailed() {
        mockPg.decline();
        String orderId = prepare();
        confirm(orderId);

        orderServer.awaitReceived(orderId, "APPROVAL_FAILED", ARRIVAL);

        assertThat(orderServer.statusOf(orderId)).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    @DisplayName("브로커가 끊긴 동안 승인된 결제도 브로커가 돌아오면 주문 서비스에 승인이 도착한다")
    void approvalMadeWhileBrokerIsCutArrivesLater() {
        String orderId = prepare();
        long cutAt = System.nanoTime();
        broker.cut();

        confirm(orderId);
        sleepUntil(cutAt, CUT);
        broker.restore();

        orderServer.awaitReceived(orderId, "APPROVED", ARRIVAL);
        assertThat(orderServer.receivedCount(orderId, "APPROVED")).isPositive();
    }

    @Test
    @DisplayName("승인 기록이 실패한 결제의 승인은 주문 서비스에 가지 않는다")
    void approvalWhoseRecordFailedIsNotAnnounced() {
        String failedOrderId = prepare();
        blockApprovalRecord();
        catchThrowable(() -> confirm(failedOrderId));
        jdbcTemplate.execute("drop trigger if exists block_approval_record");

        // 기록이 실패한 뒤에 승인한 결제의 승인이 도착할 때까지 기다리고, 그 뒤로도 1초 동안 앞 결제의 승인이 오지 않는지 본다
        String laterOrderId = approvedOrder();
        orderServer.awaitReceived(laterOrderId, "APPROVED", ARRIVAL);

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2))
                .until(() -> orderServer.receivedCount(failedOrderId, "APPROVED") == 0);
    }

    @Test
    @DisplayName("같은 결제의 승인과 취소는 만든 순서대로 주문 서비스에 도착한다")
    void approvalAndCancelArriveInOrder() {
        String orderId = approvedThenCanceledWhileApprovalIsBeingReceived();

        assertThat(orderServer.receivedStatuses(orderId)).containsExactly("APPROVED", "APPROVAL_CANCELED");
    }

    @Test
    @DisplayName("승인 뒤 전액 취소된 결제의 주문은 주문 서비스에서 취소 상태다")
    void canceledPaymentLeavesOrderCanceled() {
        String orderId = approvedThenCanceledWhileApprovalIsBeingReceived();

        assertThat(orderServer.statusOf(orderId)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("브로커가 1.5초 늦게 답해도 결제 승인은 1초 안에 답한다")
    void slowBrokerDoesNotSlowApproval() {
        // 컨텍스트가 뜬 뒤 첫 승인은 늦을 수 있어(처음 쓰는 연결·클래스) 한 번 승인해 둔 뒤 잰다
        approvedOrder();
        String orderId = prepare();
        broker.slowDown(SLOW);

        long started = System.nanoTime();
        confirm(orderId);
        Duration answeredIn = Duration.ofNanos(System.nanoTime() - started);

        assertThat(answeredIn).isLessThan(Duration.ofSeconds(1));
    }

    // 승인 이벤트를 받은 주문 서비스가 그 이벤트를 남기다 1초 붙잡힌 동안 전액 취소한다. 두 이벤트가 모두 도착할 때까지 기다린다
    String approvedThenCanceledWhileApprovalIsBeingReceived() {
        orderServer.holdReceiving("APPROVED");
        String orderId = approvedOrder();
        orderServer.awaitHeld();

        paymentCancelService.cancel(new CancelPaymentCommand(orderId, "cancel-" + orderId, null, "Customer changed their mind"));

        await().atMost(ARRIVAL).until(() -> orderServer.receivedStatuses(orderId).size() >= 2);
        return orderId;
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    void confirm(String orderId) {
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
    }

    String approvedOrder() {
        String orderId = prepare();
        confirm(orderId);
        return orderId;
    }

    // 2장과 같은 방법: 결제를 승인 상태로 바꾸는 기록을 MySQL 트리거로 막는다
    void blockApprovalRecord() {
        jdbcTemplate.execute("""
                create trigger block_approval_record before update on payment for each row
                begin
                    if new.status = 'APPROVED' then
                        signal sqlstate '45000' set message_text = 'approval record blocked for test';
                    end if;
                end""");
    }

    static void sleepUntil(long startedNanos, Duration total) {
        long left = total.toMillis() - Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
        if (left > 0) {
            try {
                Thread.sleep(left);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
