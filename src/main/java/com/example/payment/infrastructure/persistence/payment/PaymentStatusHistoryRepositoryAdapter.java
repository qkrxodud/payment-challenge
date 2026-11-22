package com.example.payment.infrastructure.persistence.payment;

import com.example.payment.domain.payment.PaymentStatusHistory;
import com.example.payment.domain.payment.PaymentStatusHistoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentStatusHistoryRepositoryAdapter implements PaymentStatusHistoryRepository {

    private final SpringDataPaymentStatusHistoryRepository springDataPaymentStatusHistoryRepository;

    @Override
    public void saveAll(List<PaymentStatusHistory> histories) {
        springDataPaymentStatusHistoryRepository.saveAll(histories);
    }
}
