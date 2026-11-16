package com.example.payment.presentation.webhook.comparison;

import com.example.payment.application.payment.PaymentWebhookRecorder;
import com.example.payment.application.payment.PaymentWebhookService;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.infrastructure.pg.LastCancelOnlyPaymentGateway;
import com.example.payment.presentation.webhook.dto.PaymentWebhookRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비교 구현 D(6장 결정 3): 받은 알림의 본문 SHA-256 을 남겨 같은 본문은 다시 받지 않고(중복 표), 그 대신 처리는 알림 한 건을
 * 변화 한 건으로 한다(LastCancelOnlyPaymentGateway). 나머지는 운영 구현(PaymentWebhookService)과 같다.
 * 같은 본문이 두 번 오면 걸러지지만, 같은 상태가 다른 createdAt 으로 다시 오면 본문이 달라 걸러지지 않는다.
 * 시험 도구라 생성자를 손으로 쓴다(운영 빈과 같은 종류의 빈을 하나 더 만들지 않으려고 서비스를 여기서 만든다).
 */
@RestController
public class BodyDigestWebhookController {

    static final String PATH = "/comparison/webhooks/body-digest";

    private final JsonMapper jsonMapper;
    private final PaymentWebhookService service;
    private final Set<String> received = ConcurrentHashMap.newKeySet();

    public BodyDigestWebhookController(JsonMapper jsonMapper, PaymentRepository paymentRepository, RestClient pgRestClient,
                                       PaymentWebhookRecorder paymentWebhookRecorder) {
        this.jsonMapper = jsonMapper;
        this.service = new PaymentWebhookService(paymentRepository, new LastCancelOnlyPaymentGateway(pgRestClient),
                paymentWebhookRecorder);
    }

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
