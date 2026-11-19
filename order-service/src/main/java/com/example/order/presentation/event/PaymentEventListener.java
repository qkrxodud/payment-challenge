package com.example.order.presentation.event;

import com.example.order.application.order.OrderPaymentService;
import com.example.order.application.order.dto.PaymentEventCommand;
import com.example.order.presentation.event.dto.PaymentEventMessage;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 9장: 결제 이벤트 토픽을 읽는다. 파티션마다 따로 읽는다(order.listener 의 concurrency — 파티션 수만큼).
 * 처리에 실패하면 Spring Kafka 기본 처리(바로 다시 시도, 10번째에 로그를 남기고 넘어감)를 그대로 둔다 — 재시도·실패 모음은 11장.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderPaymentService orderPaymentService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "${order.payment-events.topic}")
    public void receive(ConsumerRecord<String, String> record) {
        PaymentEventMessage message = jsonMapper.readValue(record.value(), PaymentEventMessage.class);
        orderPaymentService.apply(new PaymentEventCommand(message.eventId(), message.orderId(), message.status(),
                message.occurredAt(), record.partition(), record.offset()));
    }
}
