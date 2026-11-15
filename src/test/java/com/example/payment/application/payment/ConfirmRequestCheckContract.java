package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.payment.MockPgConfiguration;
import com.example.payment.MockPgServer;
import com.example.payment.TestcontainersConfiguration;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import com.example.payment.domain.payment.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 승인 요청을 믿어도 되는지 확인하는 규칙: 금액이 준비한 금액과 같은지, 같은 요청이 다시 온 것인지.
 * 금액 비교 방식과 다시 온 요청의 처리 방식이 구현마다 다르므로 구현마다 이 클래스를 상속해 같은 테스트를 돌린다.
 * 준비 금액은 15000 이고, DB 에는 소수 둘째 자리까지(15000.00) 저장된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, MockPgConfiguration.class})
abstract class ConfirmRequestCheckContract {

    private static final BigDecimal PREPARED = new BigDecimal("15000");

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
    @DisplayName("요청 금액이 준비한 금액과 다르면 카드사에 승인을 요청하지 않는다")
    void differentAmountIsNotSentToCardCompany() {
        String orderId = prepare();

        catchThrowable(() -> confirm(request(orderId, "key-" + orderId, new BigDecimal("100"))));

        assertThat(mockPg.approvedAmounts(orderId)).isEmpty();
    }

    @Test
    @DisplayName("요청 금액이 준비한 금액과 다르면 결제는 준비 상태 그대로다")
    void differentAmountLeavesPaymentReady() {
        String orderId = prepare();

        catchThrowable(() -> confirm(request(orderId, "key-" + orderId, new BigDecimal("100"))));

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("소수 자릿수만 다르게 적은 같은 금액이면 승인한다")
    void sameAmountWrittenDifferentlyIsApproved() {
        String orderId = prepare();

        confirm(request(orderId, "key-" + orderId, new BigDecimal("15000")));

        assertThat(statusOf(orderId)).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("승인된 결제에 같은 승인 요청이 다시 오면 승인으로 답한다")
    void repeatedRequestForApprovedPaymentIsAnsweredAsApproved() {
        String orderId = prepare();
        confirm(request(orderId, "key-" + orderId, PREPARED));

        PaymentResult again = confirm(request(orderId, "key-" + orderId, PREPARED));

        assertThat(again.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("승인된 결제에 같은 승인 요청이 다시 와도 카드사에는 한 번만 승인을 요청한다")
    void repeatedRequestIsSentToCardCompanyOnce() {
        String orderId = prepare();
        confirm(request(orderId, "key-" + orderId, PREPARED));

        catchThrowable(() -> confirm(request(orderId, "key-" + orderId, PREPARED)));

        assertThat(mockPg.approvalCount(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("승인이 거절된 결제에 같은 승인 요청이 다시 오면 카드사에 묻지 않고 거절로 답한다")
    void repeatedRequestForDeclinedPaymentIsAnsweredAsDeclined() {
        String orderId = prepare();
        mockPg.decline();
        catchThrowable(() -> confirm(request(orderId, "key-" + orderId, PREPARED)));
        // 지금 다시 물으면 카드사는 승인한다. 그래도 처음 결과로 답해야 한다
        mockPg.reset();

        PaymentResult again = confirm(request(orderId, "key-" + orderId, PREPARED));

        assertThat(again.status()).isEqualTo(PaymentStatus.APPROVAL_FAILED);
    }

    @Test
    @DisplayName("승인된 결제에 다른 결제 키로 승인 요청이 오면 받아들이지 않는다")
    void requestWithOtherPaymentKeyIsRefused() {
        String orderId = prepare();
        confirm(request(orderId, "key-" + orderId, PREPARED));

        Throwable failure = catchThrowable(() -> confirm(request(orderId, "other-key-" + orderId, PREPARED)));

        assertThat(failure).isNotNull();
    }

    String prepare() {
        String orderId = "order-" + UUID.randomUUID();
        paymentPrepareService.prepare(new PreparePaymentCommand(orderId, PREPARED));
        return orderId;
    }

    static ConfirmPaymentCommand request(String orderId, String paymentKey, BigDecimal amount) {
        return new ConfirmPaymentCommand(orderId, paymentKey, amount);
    }

    PaymentStatus statusOf(String orderId) {
        return paymentQueryService.find(orderId).status();
    }
}
