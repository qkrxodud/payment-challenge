package com.example.payment.infrastructure.persistence.payment.comparison;

import com.example.payment.domain.payment.Payment;
import com.example.payment.infrastructure.persistence.payment.PaymentRepositoryAdapter;
import java.util.Optional;
import org.springframework.context.annotation.Primary;

/** 비교 구현(7장 결정 1): 잠그지 않음(6장까지). 결제를 바꾸려고 읽을 때도 그냥 읽는다 */
@Primary
public class NoLockPaymentRepository extends DelegatingPaymentRepository {

    public NoLockPaymentRepository(PaymentRepositoryAdapter delegate) {
        super(delegate);
    }

    @Override
    public Optional<Payment> findByOrderIdForUpdate(String orderId) {
        return delegate.findByOrderId(orderId);
    }
}
