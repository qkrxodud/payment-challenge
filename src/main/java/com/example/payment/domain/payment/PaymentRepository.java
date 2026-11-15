package com.example.payment.domain.payment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByOrderId(String orderId);

    // 이 상태들 가운데 정한 때보다 먼저 승인을 요청한 결제. 한 번에 100건까지, 오래된 것부터
    List<Payment> findApprovalRequestedBefore(Collection<PaymentStatus> statuses, Instant requestedBefore);
}
