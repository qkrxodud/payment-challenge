package com.example.order.domain.order;

public interface ReceivedPaymentEventRepository {

    ReceivedPaymentEvent save(ReceivedPaymentEvent event);
}
