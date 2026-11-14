package com.example.payment.infrastructure.persistence.payment;

import com.example.payment.domain.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPaymentRepository extends JpaRepository<Payment, Long> {
}
