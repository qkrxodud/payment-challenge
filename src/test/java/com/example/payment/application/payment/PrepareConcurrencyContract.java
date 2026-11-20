package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payment.Attempt;
import com.example.payment.MockPgConfiguration;
import com.example.payment.SlowWrites;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 주문의 결제 준비가 동시에 올 때의 규칙(7장, 3장에서 넘긴 문제). 겹친 준비를 무엇으로 가리는지(주문번호가 두 번 올라갈 수 없다는
 * 거부를 잡아 다시 읽기, 주문번호 이름 잠금, 가리지 않기)가 구현마다 다르므로 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * 먼저 보낸 준비의 결제 기록을 1초 붙잡아 두고, 그동안 같은 주문번호의 두 번째 준비를 보낸다. 두 번째 준비는 첫 결제가 아직 보이지 않을 때
 * 주문번호로 결제를 찾는다(3장 준비는 찾아서 없으면 새로 남긴다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class PrepareConcurrencyContract {

    static final BigDecimal PREPARED = new BigDecimal("15000");

    @Autowired
    JdbcTemplate jdbcTemplate;

    SlowWrites slowWrites;

    /** 구현마다 준비 진입점을 연결한다 */
    abstract PaymentResult prepare(PreparePaymentCommand command);

    @BeforeEach
    void holdFirstPayment() {
        slowWrites = new SlowWrites(jdbcTemplate);
        slowWrites.holdNewPayment();
    }

    @AfterEach
    void releaseWrites() {
        slowWrites.release();
    }

    @Test
    @DisplayName("같은 주문을 같은 금액으로 동시에 두 번 준비해도 결제는 하나만 생긴다")
    void samePreparationsTogetherMakeOnePayment() {
        String orderId = newOrderId();

        prepareTogether(orderId, PREPARED, PREPARED);

        assertThat(paymentCountOf(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 주문을 같은 금액으로 동시에 두 번 준비하면 나중 준비도 준비된 결제로 답을 받는다")
    void laterSamePreparationIsAnsweredWithPreparedPayment() {
        String orderId = newOrderId();

        Attempt<PaymentResult> later = prepareTogether(orderId, PREPARED, PREPARED);

        assertThat(later.isAnswered()).as("나중 준비의 답: %s", later.failure()).isTrue();
        assertThat(later.result().status()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("같은 주문을 다른 금액으로 동시에 준비하면 나중 준비는 이미 있는 주문이라고 받지 않는다")
    void laterOtherAmountPreparationIsRefusedAsDuplicateOrder() {
        String orderId = newOrderId();

        Attempt<PaymentResult> later = prepareTogether(orderId, PREPARED, new BigDecimal("20000"));

        assertThat(later.errorCode()).isEqualTo(ErrorCode.DUPLICATE_ORDER);
    }

    @Test
    @DisplayName("같은 주문을 다른 금액으로 동시에 준비해도 결제 금액은 먼저 준비한 금액이다")
    void otherAmountPreparationKeepsFirstAmount() {
        String orderId = newOrderId();

        prepareTogether(orderId, PREPARED, new BigDecimal("20000"));

        assertThat(amountOf(orderId)).isEqualByComparingTo(PREPARED);
    }

    // 먼저 보낸 준비가 결제를 남기다 붙잡힌 동안 두 번째 준비를 보낸다. 두 번째 준비의 결과를 돌려준다
    Attempt<PaymentResult> prepareTogether(String orderId, BigDecimal first, BigDecimal later) {
        CompletableFuture<Attempt<PaymentResult>> firstPreparation = Attempt.start(
                () -> prepare(new PreparePaymentCommand(orderId, first)));
        slowWrites.awaitHeld();
        Attempt<PaymentResult> laterPreparation = Attempt.of(() -> prepare(new PreparePaymentCommand(orderId, later)));
        assertThat(firstPreparation.join().isAnswered()).as("먼저 보낸 준비의 답").isTrue();
        return laterPreparation;
    }

    int paymentCountOf(String orderId) {
        return jdbcTemplate.queryForObject("select count(*) from payment where order_id = ?", Integer.class, orderId);
    }

    BigDecimal amountOf(String orderId) {
        return jdbcTemplate.queryForObject("select amount from payment where order_id = ?", BigDecimal.class, orderId);
    }

    static String newOrderId() {
        return "order-" + UUID.randomUUID();
    }
}
