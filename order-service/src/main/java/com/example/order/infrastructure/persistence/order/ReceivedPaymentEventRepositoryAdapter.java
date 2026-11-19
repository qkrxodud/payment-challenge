package com.example.order.infrastructure.persistence.order;

import com.example.order.domain.order.ReceivedPaymentEvent;
import com.example.order.domain.order.ReceivedPaymentEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReceivedPaymentEventRepositoryAdapter implements ReceivedPaymentEventRepository {

    private final SpringDataReceivedPaymentEventRepository springDataReceivedPaymentEventRepository;

    @Override
    public ReceivedPaymentEvent save(ReceivedPaymentEvent event) {
        return springDataReceivedPaymentEventRepository.save(event);
    }
}
