package com.example.order.application.order.dto;

import com.example.order.domain.error.exception.InvalidPaymentEventException;
import java.time.Instant;
import java.util.Objects;

/** 9장: 받은 결제 이벤트 한 건(고유 번호, 주문번호, 결제 결과, 만든 때)과 그 이벤트가 실려 온 자리(파티션·위치) */
public record PaymentEventCommand(String eventId, String orderId, String paymentStatus, Instant occurredAt, int partition,
                                  long offset) {

    // 11장 결정 2: 고유 번호는 결제 서버 payment_outbox 의 번호다. 결정 3: 숫자가 아니면 영구 오류로 알린다
    public long eventNumber() {
        if (isNotEventNumber(eventId)) {
            throw new InvalidPaymentEventException();
        }
        return Long.parseLong(eventId);
    }

    // 11장 결정 3: 주문번호·만든 때가 없는 이벤트는 다시 시도해도 같다(영구 오류)
    public void requireComplete() {
        if (Objects.isNull(orderId) || orderId.isBlank() || Objects.isNull(occurredAt)) {
            throw new InvalidPaymentEventException();
        }
    }

    private static boolean isNotEventNumber(String value) {
        return Objects.isNull(value) || value.isEmpty() || value.length() > 18
                || value.chars().anyMatch(digit -> digit < '0' || digit > '9');
    }
}
