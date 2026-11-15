package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.comparison.InTransactionConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
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
 * 2장이 기록한 한계: PG 가 승인한 뒤 우리 쪽 승인 기록이 실패하는 경우.
 * 승인 상태로 바꾸는 저장을 MySQL 트리거로 막아 기록 실패를 만든다.
 * 실패 직후의 상태는 4장에서도 같다. 4장부터 인증 상태로 남은 결제는 보정 대기 시간이 지나면 보정 대상이 되어
 * 카드사 기록에 맞춰진다(ReconciliationContract). 하나로 묶은 구조가 남기는 준비 상태는 보정 대상이 아니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class, InTransactionConfirmService.class})
@DisplayName("카드사 승인 뒤 승인 기록이 실패하면")
class ApprovalRecordFailureTest {

    @Autowired
    MockPgServer mockPg;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentPrepareService paymentPrepareService;

    @Autowired
    PaymentQueryService paymentQueryService;

    @Autowired
    PaymentConfirmService paymentConfirmService;

    @Autowired
    InTransactionConfirmService inTransactionConfirmService;

    @BeforeEach
    void blockApprovalRecord() {
        jdbcTemplate.execute("""
                create trigger block_approval_record before update on payment for each row
                begin
                    if new.status = 'APPROVED' then
                        signal sqlstate '45000' set message_text = 'approval record blocked for test';
                    end if;
                end""");
    }

    @AfterEach
    void allowApprovalRecord() {
        jdbcTemplate.execute("drop trigger if exists block_approval_record");
    }

    @Test
    @DisplayName("나눈 구조에서는 카드사는 승인했는데 결제는 인증 상태로 남는다")
    void splitBoundaryLeavesAuthenticated() {
        String orderId = prepare();

        Throwable failure = catchThrowable(() -> paymentConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, new BigDecimal("15000"))));

        assertThat(failure).isNotNull();
        assertThat(mockPg.approvalCount(orderId)).isEqualTo(1);
        assertThat(paymentQueryService.find(orderId).status()).isEqualTo(PaymentStatus.AUTHENTICATED);
    }

    @Test
    @DisplayName("하나로 묶은 구조에서는 카드사는 승인했는데 결제는 준비 상태로 돌아간다")
    void singleBoundaryLeavesReady() {
        String orderId = prepare();

        Throwable failure = catchThrowable(() -> inTransactionConfirmService.confirm(new ConfirmPaymentCommand(orderId, "key-" + orderId, new BigDecimal("15000"))));

        assertThat(failure).isNotNull();
        assertThat(mockPg.approvalCount(orderId)).isEqualTo(1);
        assertThat(paymentQueryService.find(orderId).status()).isEqualTo(PaymentStatus.READY);
    }

    private String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, new BigDecimal("15000")));
        return orderId;
    }
}
