package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.BrokerLink;
import com.example.payment.KafkaBroker;
import com.example.payment.MockPgConfiguration;
import com.example.payment.OrderServer;
import com.example.payment.OrderServerConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 9장 결정 2: 남긴 결제 결과를 번호 순으로 읽어 보내고, 브로커가 받았다고 답하면 보낸 때를 남기는 릴레이. 시험이 주기를 직접 부른다 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, OrderServerConfiguration.class})
@DisplayName("남긴 결제 결과를 보내는 릴레이")
class PaymentOutboxRelayTest {

    static final BigDecimal PREPARED = new BigDecimal("15000");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OrderServer orderServer;

    @Autowired
    PaymentOutboxRelay paymentOutboxRelay;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    BrokerLink broker = KafkaBroker.link();

    @BeforeEach
    void clearOutbox() {
        jdbcTemplate.update("delete from payment_outbox");
    }

    @AfterEach
    void restore() {
        broker.restore();
    }

    @Test
    @DisplayName("릴레이가 돌기 전에는 승인 결과를 보내지 않는다")
    void nothingIsSentBeforeRelay() {
        String orderId = approvedOrder();

        assertThat(sentAtOf(orderId)).isZero();
    }

    @Test
    @DisplayName("브로커가 받았다고 답한 결과는 보낸 것으로 남는다")
    void confirmedOutcomeIsMarkedSent() {
        String orderId = approvedOrder();

        paymentOutboxRelay.relay();

        assertThat(sentAtOf(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("브로커가 끊겨 보내지 못한 결과는 보내지 않은 것으로 남아 다음 주기에 다시 보낸다")
    void unsentOutcomeIsSentNextTime() {
        String orderId = approvedOrder();
        broker.cut();
        paymentOutboxRelay.relay();
        broker.restore();

        paymentOutboxRelay.relay();

        orderServer.awaitReceived(orderId, "APPROVED", Duration.ofSeconds(15));
        assertThat(sentAtOf(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("브로커가 끊긴 주기에는 보낸 것으로 남기지 않는다")
    void cutBrokerLeavesUnsent() {
        String orderId = approvedOrder();
        broker.cut();

        paymentOutboxRelay.relay();

        assertThat(sentAtOf(orderId)).isZero();
    }

    // 이 주문의 결과 가운데 보낸 것으로 남은 수
    int sentAtOf(String orderId) {
        Integer sent = jdbcTemplate.queryForObject("select count(*) from payment_outbox where order_id = ? and sent_at is not null",
                Integer.class, orderId);
        return Objects.requireNonNullElse(sent, 0);
    }

    String approvedOrder() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, PREPARED));
        return orderId;
    }
}
