package com.example.payment.presentation.webhook.comparison;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비교 구현 F(6장 결정 5): 알림을 무시한다. 받았다고만 답하고 아무것도 남기지 않는다. 결과 모름 결제·취소는 4·5장 주기 보정이
 * 보정 대기 시간이 지난 뒤에 맞춘다. 카드사에서만 일어난 취소는 주기 보정이 보지 않는다(보정 대상은 우리가 요청한 취소뿐이다).
 */
@RestController
public class IgnoredWebhookController {

    static final String PATH = "/comparison/webhooks/ignored";

    @PostMapping(PATH)
    public void receive(@RequestBody String ignored) {
    }
}
