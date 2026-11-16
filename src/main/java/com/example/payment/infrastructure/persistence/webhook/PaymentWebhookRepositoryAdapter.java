package com.example.payment.infrastructure.persistence.webhook;

import com.example.payment.domain.webhook.PaymentWebhook;
import com.example.payment.domain.webhook.PaymentWebhookRepository;
import com.example.payment.domain.webhook.WebhookStatus;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentWebhookRepositoryAdapter implements PaymentWebhookRepository {

    private final SpringDataPaymentWebhookRepository springDataPaymentWebhookRepository;

    @Override
    public PaymentWebhook save(PaymentWebhook webhook) {
        return springDataPaymentWebhookRepository.save(webhook);
    }

    @Override
    public List<PaymentWebhook> findReceived() {
        return springDataPaymentWebhookRepository.findTop100ByStatusOrderByIdAsc(WebhookStatus.RECEIVED);
    }

    @Override
    public List<PaymentWebhook> findAllById(Collection<Long> ids) {
        return springDataPaymentWebhookRepository.findAllById(ids);
    }
}
