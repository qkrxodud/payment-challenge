package com.example.payment.domain.payment;

import static com.example.payment.domain.payment.PaymentAction.APPROVE;
import static com.example.payment.domain.payment.PaymentAction.AUTHENTICATE;
import static com.example.payment.domain.payment.PaymentAction.CANCEL;
import static com.example.payment.domain.payment.PaymentAction.CAPTURE;
import static com.example.payment.domain.payment.PaymentAction.EXPIRE;
import static com.example.payment.domain.payment.PaymentAction.FAIL_APPROVAL;
import static com.example.payment.domain.payment.PaymentAction.FAIL_AUTHENTICATION;
import static com.example.payment.domain.payment.PaymentAction.MARK_APPROVAL_UNKNOWN;

import com.example.payment.global.error.exception.InvalidPaymentTransitionException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public enum PaymentStatus {
    READY,              // 결제 생성, 인증 전
    AUTHENTICATED,      // 결제수단 인증 완료, 승인 전
    AUTH_FAILED,        // 인증 실패
    APPROVED,           // 승인 완료, 매입 전
    APPROVAL_FAILED,    // 승인 거절
    APPROVAL_UNKNOWN,   // 승인을 요청했지만 카드사 답을 받지 못해 결과를 모름(4장부터)
    EXPIRED,            // 승인 요청 없이 유효 시간이 지나 만료
    CAPTURED,           // 매입 완료(대금 청구 확정)
    APPROVAL_CANCELED,  // 매입 전 취소(승인 취소)
    CAPTURE_CANCELED;   // 매입 후 취소(매입 취소)

    // 허용 전이 표. 여기 없는 (상태, 행위) 조합은 모두 거부한다.
    private static final Map<PaymentStatus, Map<PaymentAction, PaymentStatus>> TRANSITIONS =
            new EnumMap<>(PaymentStatus.class);

    static {
        allow(READY, AUTHENTICATE, AUTHENTICATED);
        allow(READY, FAIL_AUTHENTICATION, AUTH_FAILED);
        allow(READY, EXPIRE, EXPIRED);

        allow(AUTHENTICATED, APPROVE, APPROVED);
        allow(AUTHENTICATED, FAIL_APPROVAL, APPROVAL_FAILED);
        allow(AUTHENTICATED, MARK_APPROVAL_UNKNOWN, APPROVAL_UNKNOWN);
        allow(AUTHENTICATED, EXPIRE, EXPIRED);

        // 결과 모름은 카드사에 확인한 결과로만 닫는다. 승인이 남았을 수 있어 만료로 닫지 않는다
        allow(APPROVAL_UNKNOWN, APPROVE, APPROVED);
        allow(APPROVAL_UNKNOWN, FAIL_APPROVAL, APPROVAL_FAILED);

        allow(APPROVED, CAPTURE, CAPTURED);
        allow(APPROVED, CANCEL, APPROVAL_CANCELED);

        allow(CAPTURED, CANCEL, CAPTURE_CANCELED);
    }

    private static void allow(PaymentStatus from, PaymentAction action, PaymentStatus to) {
        TRANSITIONS.computeIfAbsent(from, status -> new EnumMap<>(PaymentAction.class)).put(action, to);
    }

    // 승인을 요청했지만 카드사 답을 아직 남기지 못한 상태인가(기록 전 인증, 결과 모름)
    public boolean isAwaitingApprovalResult() {
        return this == AUTHENTICATED || this == APPROVAL_UNKNOWN;
    }

    // 허용 전이 표에 없는 행위인가(5장: 취소를 카드사에 보내기 전에 묻는다)
    public boolean refuses(PaymentAction action) {
        return Objects.isNull(TRANSITIONS.getOrDefault(this, Collections.emptyMap()).get(action));
    }

    // 승인이 남아 있는 상태인가(승인, 매입). 6장: 카드사 취소를 따라갈 수 있는 상태다
    public boolean holdsApproval() {
        return this == APPROVED || this == CAPTURED;
    }

    public boolean isApprovalUnknown() {
        return this == APPROVAL_UNKNOWN;
    }

    public PaymentStatus next(PaymentAction action) {
        PaymentStatus to = TRANSITIONS.getOrDefault(this, Collections.emptyMap()).get(action);
        if (Objects.isNull(to)) {
            throw new InvalidPaymentTransitionException(name(), action.name());
        }
        return to;
    }
}
