package com.example.payment.infrastructure.kafka;

import com.example.payment.global.config.PaymentEventProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** 9장: 서버가 뜰 때 결제 이벤트 토픽이 없으면 만든다(있으면 그대로 둔다). 단일 브로커라 복제본은 하나다 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic paymentEventsTopic(PaymentEventProperties paymentEventProperties) {
        return TopicBuilder.name(paymentEventProperties.getTopic())
                .partitions(paymentEventProperties.getPartitions())
                .replicas(1)
                .build();
    }
}
