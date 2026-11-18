package com.example.mockpg;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 8장: 결제 인증 뒤 승인 요청이 카드사에 닿지 않은 채 유효 시간이 지나 카드사 쪽에서 만료된 결제.
 * 실제로는 시간이 지나야 일어나는 일이라 시험이 바로 만료시킨다. 만료하면 웹훅으로 알린다
 * (토스페이먼츠 문서는 상태가 EXPIRED 로 바뀐다고만 적고, 그때 웹훅을 보낸다는 문장은 따로 없다 — 보낸다고 가정한다).
 */
@Component
@RequiredArgsConstructor
public class PaymentExpiry {

    private final ScenarioStore scenarioStore;
    private final WebhookSender webhookSender;

    public void expire(String orderId, String paymentKey, BigDecimal amount) {
        webhookSender.statusChanged(scenarioStore.recordExpiry(paymentKey, orderId, amount));
    }
}
