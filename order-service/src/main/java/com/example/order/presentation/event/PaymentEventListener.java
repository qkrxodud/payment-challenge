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
 * 11장 결정 3: 처리에 실패하면 같은 자리에서 간격을 늘려 다시 시도하고, 읽을 수 없는 본문·모르는 상태 같은 영구 오류는 다시 시도하지 않는다
 * (KafkaConsumerConfig). 9·10장은 Spring Kafka 기본 처리(바로 다시 시도, 10번째에 로그를 남기고 넘어감)였다.
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
