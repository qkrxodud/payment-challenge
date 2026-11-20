package com.example.payment.application.payment.comparison;

import com.example.payment.domain.error.exception.PaymentEventNotSentException;
import com.example.payment.domain.payment.PaymentEvent;
import com.example.payment.domain.payment.PaymentEventSender;
import com.example.payment.global.config.PaymentEventProperties;
import com.example.payment.infrastructure.kafka.dto.PaymentEventMessage;
import java.util.concurrent.CompletionException;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 비교 구현(9장 결정 4): 키 없이 보낸다(결정 3까지의 운영 보내기). 어느 파티션에 실을지는 프로듀서가 정한다. 그 밖은 운영 보내기와 같다 */
@Primary
public class UnkeyedEventSender implements PaymentEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentEventProperties paymentEventProperties;
    private final JsonMapper jsonMapper;

    public UnkeyedEventSender(KafkaTemplate<String, String> kafkaTemplate, PaymentEventProperties paymentEventProperties,
                              JsonMapper jsonMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.paymentEventProperties = paymentEventProperties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void send(PaymentEvent event) {
        String body = jsonMapper.writeValueAsString(PaymentEventMessage.from(event));
        try {
            kafkaTemplate.send(paymentEventProperties.getTopic(), body).join();
        } catch (CompletionException notConfirmed) {
            throw new PaymentEventNotSentException(event.getEventId(), notConfirmed.getCause().getClass().getSimpleName());
        } catch (KafkaException notSent) {
            throw new PaymentEventNotSentException(event.getEventId(), notSent.getClass().getSimpleName());
        }
    }
}
