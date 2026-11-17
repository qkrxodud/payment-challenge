package com.example.payment.infrastructure.persistence.payment;

import com.example.payment.domain.payment.CancelStatus;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    // 보정 한 번에 보는 결제 수(4장 승인 보정과 같은 100건)
    private static final int BATCH = 100;

    private final SpringDataPaymentRepository springDataPaymentRepository;

    @Override
    public Payment save(Payment payment) {
        return springDataPaymentRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return springDataPaymentRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return springDataPaymentRepository.findByOrderId(orderId);
    }

    @Override
    public Optional<Payment> findByOrderIdForUpdate(String orderId) {
        return springDataPaymentRepository.findLockedByOrderId(orderId);
    }

    @Override
    public List<Payment> findApprovalRequestedBefore(Collection<PaymentStatus> statuses, Instant requestedBefore) {
        return springDataPaymentRepository.findTop100ByStatusInAndAuthenticatedAtBeforeOrderByAuthenticatedAtAsc(
                statuses, requestedBefore);
    }

    @Override
    public List<Payment> findCancelRequestedBefore(Collection<CancelStatus> statuses, Instant requestedBefore) {
        return springDataPaymentRepository.findCancelRequestedBefore(statuses, requestedBefore, PageRequest.of(0, BATCH));
    }
}
