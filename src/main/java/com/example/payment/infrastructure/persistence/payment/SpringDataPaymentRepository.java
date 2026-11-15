package com.example.payment.infrastructure.persistence.payment;

import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findTop100ByStatusInAndAuthenticatedAtBeforeOrderByAuthenticatedAtAsc(
            Collection<PaymentStatus> statuses, Instant requestedBefore);
}
