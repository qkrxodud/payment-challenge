package com.example.payment.presentation.webhook;

import com.example.payment.application.payment.PaymentWebhookProcessor;
import com.example.payment.application.payment.PaymentWebhookRecorder;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.domain.webhook.PaymentWebhookRepository;
import com.example.payment.infrastructure.pg.LastCancelOnlyPaymentGateway;
import com.example.payment.presentation.webhook.comparison.WebhookComparisonConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

/** 이 비교 구현은 계약 일부를 지키지 못한다. 기본 테스트에서 빼고 ./gradlew knownFailingTest 로 돌린다 */
@Tag("known-failing")
@DisplayName("카드사 웹훅 처리 — 비교 D: 받은 본문을 남겨 같은 본문을 거르고, 알림 한 건을 변화 한 건으로 처리함")
class BodyDigestWebhookTest extends WebhookContract {

    @Override
    String webhookPath() {
        return WebhookComparisonConfiguration.BODY_DIGEST;
    }

    @Autowired
    PaymentWebhookRepository paymentWebhookRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentWebhookRecorder paymentWebhookRecorder;

    @Autowired
    RestClient pgRestClient;

    // 처리기는 운영 구현과 같고, 카드사에서 읽은 마지막 취소를 알림이 알린 새 취소로 본다
    @Override
    void process() {
        new PaymentWebhookProcessor(paymentWebhookRepository, paymentRepository, new LastCancelOnlyPaymentGateway(pgRestClient),
                paymentWebhookRecorder).process();
    }
}
