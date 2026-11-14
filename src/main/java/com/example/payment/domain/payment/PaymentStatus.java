package com.example.payment.domain.payment;

import static com.example.payment.domain.payment.PaymentAction.APPROVE;
import static com.example.payment.domain.payment.PaymentAction.AUTHENTICATE;
import static com.example.payment.domain.payment.PaymentAction.CANCEL;
import static com.example.payment.domain.payment.PaymentAction.CAPTURE;
import static com.example.payment.domain.payment.PaymentAction.EXPIRE;
import static com.example.payment.domain.payment.PaymentAction.FAIL_APPROVAL;
import static com.example.payment.domain.payment.PaymentAction.FAIL_AUTHENTICATION;

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
        allow(AUTHENTICATED, EXPIRE, EXPIRED);

        allow(APPROVED, CAPTURE, CAPTURED);
        allow(APPROVED, CANCEL, APPROVAL_CANCELED);

        allow(CAPTURED, CANCEL, CAPTURE_CANCELED);
    }

    private static void allow(PaymentStatus from, PaymentAction action, PaymentStatus to) {
        TRANSITIONS.computeIfAbsent(from, status -> new EnumMap<>(PaymentAction.class)).put(action, to);
    }

    public PaymentStatus next(PaymentAction action) {
        PaymentStatus to = TRANSITIONS.getOrDefault(this, Collections.emptyMap()).get(action);
        if (Objects.isNull(to)) {
            throw new InvalidPaymentTransitionException(name(), action.name());
        }
        return to;
    }
}
