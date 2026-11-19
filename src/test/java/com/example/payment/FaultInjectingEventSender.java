package com.example.payment;

import com.example.payment.domain.payment.PaymentEvent;
import com.example.payment.domain.payment.PaymentEventSender;
import com.example.payment.infrastructure.kafka.KafkaPaymentEventSender;
import org.springframework.context.annotation.Primary;

/** 9장: 운영 보내기 앞에서 SendFaults 가 정한 붙잡기·실패를 끼운다. 정한 것이 없으면 운영 보내기 그대로다 */
@Primary
public class FaultInjectingEventSender implements PaymentEventSender {

    private final KafkaPaymentEventSender kafkaPaymentEventSender;

    public FaultInjectingEventSender(KafkaPaymentEventSender kafkaPaymentEventSender) {
        this.kafkaPaymentEventSender = kafkaPaymentEventSender;
    }

    @Override
    public void send(PaymentEvent event) {
        SendFaults.beforeSend(event);
        kafkaPaymentEventSender.send(event);
    }
}
