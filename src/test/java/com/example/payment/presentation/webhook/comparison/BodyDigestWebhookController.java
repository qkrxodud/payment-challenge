package com.example.payment.presentation.webhook.comparison;

import com.example.payment.application.payment.PaymentWebhookService;
import com.example.payment.presentation.webhook.dto.PaymentWebhookRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비교 구현 D(6장 결정 3): 받은 알림의 본문 SHA-256 을 남겨 같은 본문은 다시 받지 않고(중복 표), 그 대신 처리는 알림 한 건을
 * 변화 한 건으로 한다(LastCancelOnlyPaymentGateway, 처리기는 BodyDigestWebhookTest 가 만든다). 거르고 남은 알림은 운영 구현
 * (PaymentWebhookService)으로 받는다. 같은 본문이 두 번 오면 걸러지지만, 같은 상태가 다른 createdAt 으로 다시 오면 본문이 달라
 * 걸러지지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class BodyDigestWebhookController {

    static final String PATH = "/comparison/webhooks/body-digest";

    private final JsonMapper jsonMapper;
    private final PaymentWebhookService service;
    private final Set<String> received = ConcurrentHashMap.newKeySet();

    @PostMapping(PATH)
    public void receive(@RequestBody String body) {
        if (received.add(sha256(body))) {
            service.receive(jsonMapper.readValue(body, PaymentWebhookRequest.class).toCommand());
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is not available.", missing);
        }
    }
}
