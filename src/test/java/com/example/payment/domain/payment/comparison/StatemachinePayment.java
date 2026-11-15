package com.example.payment.domain.payment.comparison;

import static com.example.payment.domain.payment.PaymentAction.APPROVE;
import static com.example.payment.domain.payment.PaymentAction.AUTHENTICATE;
import static com.example.payment.domain.payment.PaymentAction.CANCEL;
import static com.example.payment.domain.payment.PaymentAction.CAPTURE;
import static com.example.payment.domain.payment.PaymentAction.EXPIRE;
import static com.example.payment.domain.payment.PaymentAction.FAIL_APPROVAL;
import static com.example.payment.domain.payment.PaymentAction.FAIL_AUTHENTICATION;
import static com.example.payment.domain.payment.PaymentAction.MARK_APPROVAL_UNKNOWN;
import static com.example.payment.domain.payment.PaymentStatus.APPROVAL_CANCELED;
import static com.example.payment.domain.payment.PaymentStatus.APPROVAL_FAILED;
import static com.example.payment.domain.payment.PaymentStatus.APPROVAL_UNKNOWN;
import static com.example.payment.domain.payment.PaymentStatus.APPROVED;
import static com.example.payment.domain.payment.PaymentStatus.AUTHENTICATED;
import static com.example.payment.domain.payment.PaymentStatus.AUTH_FAILED;
import static com.example.payment.domain.payment.PaymentStatus.CAPTURED;
import static com.example.payment.domain.payment.PaymentStatus.CAPTURE_CANCELED;
import static com.example.payment.domain.payment.PaymentStatus.EXPIRED;
import static com.example.payment.domain.payment.PaymentStatus.READY;

import com.example.payment.domain.payment.PaymentAction;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.InvalidPaymentTransitionException;
import java.util.EnumSet;
import java.util.Objects;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineBuilder;
import reactor.core.publisher.Mono;

/** 비교 구현: Spring Statemachine. 전이를 빌더로 선언하고, 거부된 이벤트는 예외로 바꾼다. */
public class StatemachinePayment {

    private final StateMachine<PaymentStatus, PaymentAction> machine;

    private StatemachinePayment(StateMachine<PaymentStatus, PaymentAction> machine) {
        this.machine = machine;
    }

    public static StatemachinePayment create() {
        StateMachine<PaymentStatus, PaymentAction> machine = buildMachine();
        machine.startReactively().block();
        return new StatemachinePayment(machine);
    }

    private static StateMachine<PaymentStatus, PaymentAction> buildMachine() {
        try {
            StateMachineBuilder.Builder<PaymentStatus, PaymentAction> builder = StateMachineBuilder.builder();
            builder.configureStates().withStates()
                    .initial(READY)
                    .states(EnumSet.allOf(PaymentStatus.class));
            builder.configureTransitions()
                    .withExternal().source(READY).target(AUTHENTICATED).event(AUTHENTICATE).and()
                    .withExternal().source(READY).target(AUTH_FAILED).event(FAIL_AUTHENTICATION).and()
                    .withExternal().source(READY).target(EXPIRED).event(EXPIRE).and()
                    .withExternal().source(AUTHENTICATED).target(APPROVED).event(APPROVE).and()
                    .withExternal().source(AUTHENTICATED).target(APPROVAL_FAILED).event(FAIL_APPROVAL).and()
                    .withExternal().source(AUTHENTICATED).target(APPROVAL_UNKNOWN).event(MARK_APPROVAL_UNKNOWN).and()
                    .withExternal().source(AUTHENTICATED).target(EXPIRED).event(EXPIRE).and()
                    .withExternal().source(APPROVAL_UNKNOWN).target(APPROVED).event(APPROVE).and()
                    .withExternal().source(APPROVAL_UNKNOWN).target(APPROVAL_FAILED).event(FAIL_APPROVAL).and()
                    .withExternal().source(APPROVED).target(CAPTURED).event(CAPTURE).and()
                    .withExternal().source(APPROVED).target(APPROVAL_CANCELED).event(CANCEL).and()
                    .withExternal().source(CAPTURED).target(CAPTURE_CANCELED).event(CANCEL);
            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the payment state machine.", e);
        }
    }

    public void perform(PaymentAction action) {
        PaymentStatus from = getStatus();
        StateMachineEventResult<PaymentStatus, PaymentAction> result = machine
                .sendEvent(Mono.just(MessageBuilder.withPayload(action).build()))
                .blockLast();
        if (isRejected(result)) {
            throw new InvalidPaymentTransitionException(from.name(), action.name());
        }
    }

    public PaymentStatus getStatus() {
        return machine.getState().getId();
    }

    // 이벤트가 거부되어도 예외가 나지 않고 결과 타입이 DENIED 로 돌아온다
    private boolean isRejected(StateMachineEventResult<PaymentStatus, PaymentAction> result) {
        return Objects.isNull(result) || result.getResultType() != StateMachineEventResult.ResultType.ACCEPTED;
    }
}
