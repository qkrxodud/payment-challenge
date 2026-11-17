package com.example.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.application.payment.dto.PreparePaymentCommand;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

@DisplayName("결제 준비 — 남기지 못한 준비")
class PaymentPrepareServiceTest {

    @Test
    @DisplayName("주문번호가 겹친 것이 아닌 이유로 남기지 못한 준비는 다시 읽어도 결제가 없어 그 이유를 그대로 알린다")
    void refusalWithoutPaymentIsPassedOn() {
        DataIntegrityViolationException refused = new DataIntegrityViolationException("refused for test");
        PaymentPrepareService service = new PaymentPrepareService(new PaymentPrepareRecorder(null) {
            @Override
            public PaymentResult prepare(PreparePaymentCommand command) {
                throw refused;
            }

            @Override
            public Optional<PaymentResult> prepareAgain(PreparePaymentCommand command) {
                return Optional.empty();
            }
        });

        Throwable failure = catchThrowable(() -> service.prepare(new PreparePaymentCommand("order-1", new BigDecimal("15000"))));

        assertThat(failure).isSameAs(refused);
    }
}
