package com.example.payment.domain.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 9장: 주문 서비스에 알릴 결제 결과 기록 */
public interface PaymentOutboxRepository {

    PaymentOutbox save(PaymentOutbox outbox);

    // 아직 보내지 않은 기록. 번호 순으로 limit 건까지
    List<PaymentOutbox> findUnsent(int limit);

    // 브로커가 받았다고 답한 때를 남긴다. 어느 트랜잭션에도 끼지 않고 바로 반영된다
    void markSent(Long id, Instant sentAt);

    // 12장 결정 5: 아직 보내지 않은 기록의 수(운영 지표 — 릴레이가 밀린 정도)
    long countUnsent();

    // 12장 결정 5: 아직 보내지 않은 기록 가운데 가장 먼저 남긴 때. 없으면 비어 있다
    Optional<Instant> findOldestUnsentOccurredAt();

    // 12장 결정 5: 이때보다 먼저 보낸 기록을 limit 줄까지 지우고 지운 수를 돌려준다. 보내지 않은 기록은 지우지 않는다
    int deleteSentBefore(Instant cutoff, int limit);
}
