package com.example.payment.infrastructure.kafka;

import com.example.payment.domain.payment.PaymentEvent;
import com.example.payment.domain.payment.PaymentEventSender;
import com.example.payment.global.config.PaymentEventProperties;
import com.example.payment.global.error.exception.PaymentEventNotSentException;
import com.example.payment.infrastructure.kafka.dto.PaymentEventMessage;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 9장: 결제 이벤트를 JSON 으로 결제 이벤트 토픽에 보내고, 브로커가 받았다고 답할 때까지 기다린다.
 * 키 없이 보낸다 — 어느 파티션에 실을지는 프로듀서가 정한다.
 * 기다리는 시간의 끝은 프로듀서 설정(delivery.timeout.ms, 기본 2분)이 정한다.
 */
@Component
@RequiredArgsConstructor
public class KafkaPaymentEventSender implements PaymentEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentEventProperties paymentEventProperties;
    private final JsonMapper jsonMapper;

    @Override
    public void send(PaymentEvent event) {
        String body = jsonMapper.writeValueAsString(PaymentEventMessage.from(event));
        try {
            kafkaTemplate.send(paymentEventProperties.getTopic(), body).join();
        } catch (CompletionException notConfirmed) {
            throw new PaymentEventNotSentException(event.getEventId(), notConfirmed.getCause().getClass().getSimpleName());
        } catch (KafkaException notSent) {
            // 토픽 정보를 정한 시간(max.block.ms) 안에 받지 못하면 보내기 전에 실패한다
            throw new PaymentEventNotSentException(event.getEventId(), notSent.getClass().getSimpleName());
        }
    }
}
