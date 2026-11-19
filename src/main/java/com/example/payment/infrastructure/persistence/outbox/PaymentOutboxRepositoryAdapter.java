package com.example.payment.infrastructure.persistence.outbox;

import com.example.payment.domain.outbox.PaymentOutbox;
import com.example.payment.domain.outbox.PaymentOutboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
}
