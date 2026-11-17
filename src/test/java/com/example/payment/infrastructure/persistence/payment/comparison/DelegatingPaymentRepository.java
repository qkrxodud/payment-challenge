package com.example.payment.infrastructure.persistence.payment.comparison;

import com.example.payment.domain.payment.CancelStatus;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.payment.PaymentStatus;
import com.example.payment.infrastructure.persistence.payment.PaymentRepositoryAdapter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 7장 결정 1 비교 구현의 바탕. 결제를 바꾸려고 읽는 방법(findByOrderIdForUpdate)만 바꿔 끼우고 나머지는 운영 저장소에 맡긴다.
 * 비교 구현은 운영 서비스(승인·취소·알림 반영·보정)를 그대로 쓰고, 이 저장소를 우선 빈(@Primary)으로 올려 읽는 방법만 바꾼다.
 */
abstract class DelegatingPaymentRepository implements PaymentRepository {

    final PaymentRepositoryAdapter delegate;

    DelegatingPaymentRepository(PaymentRepositoryAdapter delegate) {
        this.delegate = delegate;
    }

    @Override
    public Payment save(Payment payment) {
        return delegate.save(payment);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return delegate.findByOrderId(orderId);
    }

    @Override
    public List<Payment> findApprovalRequestedBefore(Collection<PaymentStatus> statuses, Instant requestedBefore) {
        return delegate.findApprovalRequestedBefore(statuses, requestedBefore);
    }

    @Override
    public List<Payment> findCancelRequestedBefore(Collection<CancelStatus> statuses, Instant requestedBefore) {
        return delegate.findCancelRequestedBefore(statuses, requestedBefore);
    }
}
