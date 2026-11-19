package com.example.payment.infrastructure.persistence.outbox;

import com.example.payment.domain.outbox.PaymentOutbox;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPaymentOutboxRepository extends JpaRepository<PaymentOutbox, Long> {

    List<PaymentOutbox> findBySentAtIsNullOrderByIdAsc(Pageable pageable);
}
