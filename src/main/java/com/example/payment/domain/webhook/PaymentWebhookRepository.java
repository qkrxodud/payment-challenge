package com.example.payment.domain.webhook;

import java.util.Collection;
import java.util.List;

public interface PaymentWebhookRepository {

    PaymentWebhook save(PaymentWebhook webhook);

    // 아직 처리하지 않은 알림. 한 번에 100건까지, 받은 순서대로
    List<PaymentWebhook> findReceived();

    List<PaymentWebhook> findAllById(Collection<Long> ids);
}
