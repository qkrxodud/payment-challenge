package com.example.payment.infrastructure.kafka;

import com.example.payment.global.config.InventoryProperties;
import com.example.payment.global.config.PaymentEventProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** 9장: 서버가 뜰 때 결제 이벤트 토픽(10장부터 재고 명령 토픽도)이 없으면 만든다(있으면 그대로 둔다). 단일 브로커라 복제본은 하나다 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic paymentEventsTopic(PaymentEventProperties paymentEventProperties) {
        return TopicBuilder.name(paymentEventProperties.getTopic())
                .partitions(paymentEventProperties.getPartitions())
                .replicas(1)
                .build();
    }

    // 10장 결정 5: 재고 서비스에 보내는 예약 확정·해제 토픽. 결제 이벤트 토픽과 같은 파티션 수로 만든다
    @Bean
    NewTopic stockCommandsTopic(InventoryProperties inventoryProperties, PaymentEventProperties paymentEventProperties) {
        return TopicBuilder.name(inventoryProperties.getCommandsTopic())
                .partitions(paymentEventProperties.getPartitions())
                .replicas(1)
                .build();
    }
}
