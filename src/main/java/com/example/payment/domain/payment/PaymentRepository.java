package com.example.payment.domain.payment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByOrderId(String orderId);

    // 7장: 결제를 바꾸려고 읽는다. 결제 줄을 잠그고 읽어(select … for update), 같은 결제를 바꾸려는 다른 기록은 이 기록이 끝날 때까지
    // 기다렸다가 이 기록이 남긴 결제를 읽는다
    Optional<Payment> findByOrderIdForUpdate(String orderId);

    // 이 상태들 가운데 정한 때보다 먼저 승인을 요청한 결제. 한 번에 100건까지, 오래된 것부터
    List<Payment> findApprovalRequestedBefore(Collection<PaymentStatus> statuses, Instant requestedBefore);

    // 5장: 이 상태의 취소를 정한 때보다 먼저 요청한 결제. 한 번에 100건까지, 결제 번호 순으로
    List<Payment> findCancelRequestedBefore(Collection<CancelStatus> statuses, Instant requestedBefore);
}
