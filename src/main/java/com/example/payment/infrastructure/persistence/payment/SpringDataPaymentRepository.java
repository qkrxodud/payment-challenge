package com.example.payment.infrastructure.persistence.payment;

import com.example.payment.domain.payment.CancelStatus;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findTop100ByStatusInAndAuthenticatedAtBeforeOrderByAuthenticatedAtAsc(
            Collection<PaymentStatus> statuses, Instant requestedBefore);

    @Query("""
            select p from Payment p
            where exists (select c.id from PaymentCancel c
                          where c.payment = p and c.status in :statuses and c.requestedAt < :requestedBefore)
            order by p.id""")
    List<Payment> findCancelRequestedBefore(@Param("statuses") Collection<CancelStatus> statuses,
                                            @Param("requestedBefore") Instant requestedBefore, Pageable page);
}
