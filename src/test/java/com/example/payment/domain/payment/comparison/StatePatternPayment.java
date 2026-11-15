package com.example.payment.domain.payment.comparison;

import com.example.payment.domain.payment.PaymentAction;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.global.error.exception.InvalidPaymentTransitionException;

/**
 * 비교 구현: State 패턴. 상태마다 객체를 두고, 그 상태에서 허용되는 행위만 재정의한다.
 * 재정의하지 않은 행위는 기본 구현이 거부한다.
 */
public class StatePatternPayment {

    private State state;

    private StatePatternPayment(State state) {
        this.state = state;
    }

    public static StatePatternPayment create() {
        return new StatePatternPayment(new Ready());
    }

    public void authenticate() {
        state = state.authenticate();
    }

    public void failAuthentication() {
        state = state.failAuthentication();
    }

    public void approve() {
        state = state.approve();
    }

    public void failApproval() {
        state = state.failApproval();
    }

    public void markApprovalUnknown() {
        state = state.markApprovalUnknown();
    }

    public void expire() {
        state = state.expire();
    }

    public void capture() {
        state = state.capture();
    }

    public void cancel() {
        state = state.cancel();
    }

    public PaymentStatus getStatus() {
        return state.status();
    }

    abstract static class State {
        abstract PaymentStatus status();

        State authenticate() { return reject(PaymentAction.AUTHENTICATE); }
        State failAuthentication() { return reject(PaymentAction.FAIL_AUTHENTICATION); }
        State approve() { return reject(PaymentAction.APPROVE); }
        State failApproval() { return reject(PaymentAction.FAIL_APPROVAL); }
        State markApprovalUnknown() { return reject(PaymentAction.MARK_APPROVAL_UNKNOWN); }
        State expire() { return reject(PaymentAction.EXPIRE); }
        State capture() { return reject(PaymentAction.CAPTURE); }
        State cancel() { return reject(PaymentAction.CANCEL); }

        private State reject(PaymentAction action) {
            throw new InvalidPaymentTransitionException(status().name(), action.name());
        }
    }

    static class Ready extends State {
        PaymentStatus status() { return PaymentStatus.READY; }
        State authenticate() { return new Authenticated(); }
        State failAuthentication() { return new Terminal(PaymentStatus.AUTH_FAILED); }
        State expire() { return new Terminal(PaymentStatus.EXPIRED); }
    }

    static class Authenticated extends State {
        PaymentStatus status() { return PaymentStatus.AUTHENTICATED; }
        State approve() { return new Approved(); }
        State failApproval() { return new Terminal(PaymentStatus.APPROVAL_FAILED); }
        State expire() { return new Terminal(PaymentStatus.EXPIRED); }
    }

    static class Approved extends State {
        PaymentStatus status() { return PaymentStatus.APPROVED; }
        State capture() { return new Captured(); }
        State cancel() { return new Terminal(PaymentStatus.APPROVAL_CANCELED); }
    }

    static class Captured extends State {
        PaymentStatus status() { return PaymentStatus.CAPTURED; }
        State cancel() { return new Terminal(PaymentStatus.CAPTURE_CANCELED); }
    }

    /** 더 갈 곳이 없는 상태(실패, 만료, 취소) */
    static class Terminal extends State {
        private final PaymentStatus status;

        Terminal(PaymentStatus status) { this.status = status; }

        PaymentStatus status() { return status; }
    }
}
