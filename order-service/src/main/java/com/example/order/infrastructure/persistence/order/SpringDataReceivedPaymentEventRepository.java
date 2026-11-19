package com.example.order.infrastructure.persistence.order;

import com.example.order.domain.order.ReceivedPaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReceivedPaymentEventRepository extends JpaRepository<ReceivedPaymentEvent, Long> {
}
