package com.example.payment.infrastructure.persistence.payment;

import com.example.payment.domain.payment.PaymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {
}
