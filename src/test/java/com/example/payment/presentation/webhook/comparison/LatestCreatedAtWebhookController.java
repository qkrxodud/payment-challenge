package com.example.payment.presentation.webhook.comparison;

import com.example.payment.application.payment.PaymentWebhookRecorder;
import com.example.payment.presentation.webhook.comparison.dto.ReportedWebhook;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비교 구현 C(6장 결정 2): createdAt 으로 최신 알림만 받는다. 본문을 믿는 것은 비교 A 와 같고, 주문마다 이미 받은 알림보다
 * createdAt 이 이른 알림은 옮기지 않고 200 으로 답한다. 시각 비교는 createdAt 글자(yyyy-MM-dd'T'HH:mm:ss.SSSSSS)를 그대로 비교한다.
 * 순서가 바뀐 알림을 시각으로 거르는 방식은 본문을 믿을 때에만 뜻이 있어(조회로 다시 읽으면 늘 지금 상태다) 비교 A 위에 얹었다.
 */
@RestController
@RequiredArgsConstructor
public class LatestCreatedAtWebhookController {

    static final String PATH = "/comparison/webhooks/latest-created-at";

    private final PaymentWebhookRecorder paymentWebhookRecorder;
    // 주문마다 옮긴 알림 가운데 가장 늦은 createdAt
    private final Map<String, String> latest = new ConcurrentHashMap<>();

    @PostMapping(PATH)
    public void receive(@RequestBody ReportedWebhook webhook) {
        if (webhook.isPaymentStatusChange() && isLatest(webhook)) {
            paymentWebhookRecorder.follow(webhook.orderId(), webhook.reported());
        }
    }

    private boolean isLatest(ReportedWebhook webhook) {
        String kept = latest.merge(webhook.orderId(), webhook.createdAt(),
                (before, arrived) -> arrived.compareTo(before) >= 0 ? arrived : before);
        return kept.equals(webhook.createdAt());
    }
}
