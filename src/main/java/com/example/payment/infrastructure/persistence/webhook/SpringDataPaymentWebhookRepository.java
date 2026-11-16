package com.example.payment.infrastructure.persistence.webhook;

import com.example.payment.domain.webhook.PaymentWebhook;
import com.example.payment.domain.webhook.WebhookStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPaymentWebhookRepository extends JpaRepository<PaymentWebhook, Long> {

    List<PaymentWebhook> findTop100ByStatusOrderByIdAsc(WebhookStatus status);
}
