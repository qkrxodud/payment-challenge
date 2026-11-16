package com.example.payment.presentation.webhook.comparison;

import com.example.payment.application.payment.comparison.InRequestWebhookService;
import com.example.payment.application.payment.comparison.RejectOffTableWebhookService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 6장 웹훅 비교 구현들. 구현마다 다른 주소로 알림을 받는다. 웹훅 계약 테스트가 모두 같은 서버(같은 시험 환경)를 쓰도록
 * 운영 구현 시험에도 함께 올린다 — 주소가 달라 운영 구현의 동작에는 끼어들지 않는다
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({BodyStatusWebhookController.class, RejectOffTableWebhookController.class, RejectOffTableWebhookService.class,
        LatestCreatedAtWebhookController.class, BodyDigestWebhookController.class, InRequestWebhookController.class,
        InRequestWebhookService.class, IgnoredWebhookController.class})
public class WebhookComparisonConfiguration {

    public static final String BODY_STATUS = BodyStatusWebhookController.PATH;
    public static final String REJECT_OFF_TABLE = RejectOffTableWebhookController.PATH;
    public static final String LATEST_CREATED_AT = LatestCreatedAtWebhookController.PATH;
    public static final String BODY_DIGEST = BodyDigestWebhookController.PATH;
    public static final String IN_REQUEST = InRequestWebhookController.PATH;
    public static final String IGNORED = IgnoredWebhookController.PATH;
}
