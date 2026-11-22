package com.example.payment.infrastructure.persistence.outbox;

import com.example.payment.domain.outbox.PaymentOutbox;
import com.example.payment.domain.outbox.PaymentOutboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentOutboxRepositoryAdapter implements PaymentOutboxRepository {

    private final SpringDataPaymentOutboxRepository springDataPaymentOutboxRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public PaymentOutbox save(PaymentOutbox outbox) {
        return springDataPaymentOutboxRepository.save(outbox);
    }

    @Override
    public List<PaymentOutbox> findUnsent(int limit) {
        return springDataPaymentOutboxRepository.findBySentAtIsNullOrderByIdAsc(PageRequest.of(0, limit));
    }

    @Override
    public void markSent(Long id, Instant sentAt) {
        jdbcTemplate.update("update payment_outbox set sent_at = ? where id = ?", Timestamp.from(sentAt), id);
    }

    @Override
    public long countUnsent() {
        Long unsent = jdbcTemplate.queryForObject("select count(*) from payment_outbox where sent_at is null", Long.class);
        return Objects.requireNonNullElse(unsent, 0L);
    }

    // 남긴 때(occurred_at)는 JPA 가 UTC 시각으로 남긴다
    @Override
    public Optional<Instant> findOldestUnsentOccurredAt() {
        LocalDateTime oldest = jdbcTemplate.queryForObject("select min(occurred_at) from payment_outbox where sent_at is null",
                LocalDateTime.class);
        return Optional.ofNullable(oldest).map(at -> at.toInstant(ZoneOffset.UTC));
    }

    // 보낸 때(sent_at)는 markSent 와 같이 JDBC Timestamp 로 견준다
    @Override
    public int deleteSentBefore(Instant cutoff, int limit) {
        return jdbcTemplate.update("delete from payment_outbox where sent_at < ? order by id limit ?", Timestamp.from(cutoff), limit);
    }
}
