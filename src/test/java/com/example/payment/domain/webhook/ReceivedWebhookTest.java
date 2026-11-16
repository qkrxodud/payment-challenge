package com.example.payment.domain.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("받은 카드사 웹훅")
class ReceivedWebhookTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-11-16T01:00:00Z");
    private static final Instant PROCESSED_AT = Instant.parse("2026-11-16T01:00:01Z");

    @Test
    @DisplayName("받은 알림은 처리 전 알림으로 남는다")
    void receivedWebhookAwaitsProcessing() {
        PaymentWebhook webhook = received();

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.RECEIVED);
    }

    @Test
    @DisplayName("카드사 조회로 결제를 맞추면 처리한 알림이 된다")
    void processedWebhookIsClosed() {
        PaymentWebhook webhook = received();

        webhook.process(PROCESSED_AT);

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.PROCESSED);
    }

    @Test
    @DisplayName("모르는 주문의 알림은 볼 결제가 없는 알림으로 닫힌다")
    void unknownOrderWebhookIsIgnored() {
        PaymentWebhook webhook = received();

        webhook.ignore(PROCESSED_AT);

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.IGNORED);
    }

    @Test
    @DisplayName("이미 처리한 알림은 다시 닫아도 처음 처리한 때가 바뀌지 않는다")
    void processedWebhookKeepsFirstProcessedTime() {
        PaymentWebhook webhook = received();
        webhook.process(PROCESSED_AT);

        webhook.process(PROCESSED_AT.plusSeconds(60));

        assertThat(webhook.getProcessedAt()).isEqualTo(PROCESSED_AT);
    }

    @Test
    @DisplayName("카드사 조회에 실패한 알림은 처리 전 알림으로 남아 다음 처리 때 다시 본다")
    void failedLookupKeepsWebhookReceived() {
        PaymentWebhook webhook = received();

        webhook.failLookup();

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.RECEIVED);
    }

    @Test
    @DisplayName("카드사 조회에 실패할 때마다 실패 횟수가 하나씩 는다")
    void failedLookupsAreCounted() {
        PaymentWebhook webhook = received();

        webhook.failLookup();
        webhook.failLookup();

        assertThat(webhook.getFailedLookups()).isEqualTo(2);
    }

    private static PaymentWebhook received() {
        return PaymentWebhook.received("order-20261116-0001", "PAYMENT_STATUS_CHANGED", "DONE",
                "2026-11-16T10:00:00.000000", RECEIVED_AT);
    }
}
