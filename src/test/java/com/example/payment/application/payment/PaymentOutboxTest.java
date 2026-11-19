package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.payment.MockPgConfiguration;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.CancelPaymentCommand;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 9장 결정 1: 결제 상태를 바꾼 그 기록과 함께 남기는, 주문 서비스에 알릴 결제 결과 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
@DisplayName("주문 서비스에 알릴 결제 결과")
class PaymentOutboxTest {

    static final BigDecimal PREPARED = new BigDecimal("15000");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    PaymentCancelService paymentCancelService;

    @AfterEach
    void allowApprovalRecord() {
        jdbcTemplate.execute("drop trigger if exists block_approval_record");
    }

    @Test
    @DisplayName("승인된 결제는 알릴 승인 결과 하나를 남긴다")
    void approvalLeavesOneOutcome() {
        String orderId = approvedOrder();

        assertThat(outcomesOf(orderId)).containsExactly("APPROVED");
    }

    @Test
    @DisplayName("승인 기록이 실패한 결제는 알릴 결과를 남기지 않는다")
    void failedApprovalRecordLeavesNothing() {
        String orderId = prepare();
        jdbcTemplate.execute("""
                create trigger block_approval_record before update on payment for each row
                begin
                    if new.status = 'APPROVED' then
                        signal sqlstate '45000' set message_text = 'approval record blocked for test';
                    end if;
                end""");

        catchThrowable(() -> confirm(orderId));

        assertThat(outcomesOf(orderId)).isEmpty();
    }

    @Test
    @DisplayName("부분 취소처럼 결제 상태가 그대로인 기록은 알릴 결과를 남기지 않는다")
    void partialCancelLeavesNoOutcome() {
        String orderId = approvedOrder();

        paymentCancelService.cancel(new CancelPaymentCommand(orderId, "cancel-" + orderId, new BigDecimal("5000"), "Partly returned"));

        assertThat(outcomesOf(orderId)).containsExactly("APPROVED");
    }

    @Test
    @DisplayName("승인 뒤 전액 취소된 결제는 승인과 취소를 만든 순서대로 남긴다")
    void approvalThenCancelInOrder() {
        String orderId = approvedOrder();

        paymentCancelService.cancel(new CancelPaymentCommand(orderId, "cancel-" + orderId, null, "Customer changed their mind"));

        assertThat(outcomesOf(orderId)).containsExactly("APPROVED", "APPROVAL_CANCELED");
    }

    List<String> outcomesOf(String orderId) {
        return jdbcTemplate.queryForList("select status from payment_outbox where order_id = ? order by id", String.class, orderId);
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
}
