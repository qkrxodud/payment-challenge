package com.example.payment.domain.outbox;

import java.time.Instant;
import java.util.List;

/** 9장: 주문 서비스에 알릴 결제 결과 기록 */
public interface PaymentOutboxRepository {

    PaymentOutbox save(PaymentOutbox outbox);

    // 아직 보내지 않은 기록. 번호 순으로 limit 건까지
    List<PaymentOutbox> findUnsent(int limit);

    // 브로커가 받았다고 답한 때를 남긴다. 어느 트랜잭션에도 끼지 않고 바로 반영된다
    void markSent(Long id, Instant sentAt);
}
