package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("결제가 거친 상태 전이")
class PaymentTransitionRecordTest {

    @Test
    @DisplayName("상태가 바뀔 때마다 이전 상태와 행위와 다음 상태가 차례로 남는다")
    void eachChangeIsKept() {
        Payment payment = Payment.create("order-1", new BigDecimal("15000"));

        payment.authenticate("key-1", Instant.now());
        payment.markApprovalUnknown();
        payment.approve();

        assertThat(payment.drainTransitions())
                .extracting(transition -> transition.getFrom() + " " + transition.getAction() + " " + transition.getTo())
                .containsExactly("READY AUTHENTICATE AUTHENTICATED", "AUTHENTICATED MARK_APPROVAL_UNKNOWN APPROVAL_UNKNOWN",
                        "APPROVAL_UNKNOWN APPROVE APPROVED");
    }

    @Test
    @DisplayName("한 번 꺼낸 전이는 다시 꺼내도 나오지 않는다")
    void drainedTransitionsAreGone() {
        Payment payment = Payment.create("order-1", new BigDecimal("15000"));
        payment.authenticate("key-1", Instant.now());
        payment.drainTransitions();

        assertThat(payment.drainTransitions()).isEmpty();
    }

    @Test
    @DisplayName("허용 전이 표에 없어 받지 않은 행위는 전이로 남지 않는다")
    void refusedActionLeavesNothing() {
        Payment payment = Payment.create("order-1", new BigDecimal("15000"));

        // 준비 상태에서는 승인할 수 없다
        catchThrowable(payment::approve);

        assertThat(payment.drainTransitions()).isEmpty();
    }
}
