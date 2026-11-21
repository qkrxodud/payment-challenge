package com.example.inventory.infrastructure.kafka;

import com.example.inventory.domain.error.exception.InvalidStockCommandException;
import com.example.inventory.domain.error.exception.UnknownStockCommandException;
import com.example.inventory.global.config.DeadLetterProperties;
import com.example.inventory.global.config.RetryProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

/**
 * 11장 결정 3: 재고 명령 처리에 실패하면 같은 자리에서(뒤 명령을 읽지 않고) 간격을 늘려 가며 다시 시도한다(blocking).
 * 같은 예약(같은 키)의 다음 명령은 앞 명령이 끝날 때까지 기다려, 다시 시도하는 동안에도 결제 서버가 보낸 순서대로 반영한다.
 * 다시 시도해도 결과가 같은 영구 오류(읽을 수 없는 본문, 모르는 명령, 명령 번호·예약 번호가 없거나 명령 번호가 숫자가 아님)는 다시 시도하지 않는다 —
 * 같은 파티션의 뒤 명령을 다시 시도 시간만큼 붙잡지 않는다. 그 밖의 오류(DB 가 쓰기를 거부함 등)는 일시 오류로 본다.
 * 11장 결정 4: 다시 시도를 다 쓴 명령과 영구 오류 명령은 실패 모음(<토픽>.DLT)의 같은 번호 파티션에 원래 키·본문 그대로 싣는다.
 * Spring Kafka 의 DeadLetterPublishingRecoverer 가 원인(예외 종류·문장·스택)과 원래 자리(토픽·파티션·위치·시각)를 머리(kafka_dlt-*)에 붙인다.
 * 실패 모음에 실린 뒤에야 그 자리를 넘어간다 — 실패 모음에 싣지 못하면 넘어가지 않고 다시 시도한다. Spring Boot 는 이 처리기 빈을 리스너 컨테이너에 쓴다.
 */
@Configuration
@EnableConfigurationProperties({RetryProperties.class, DeadLetterProperties.class})
public class KafkaConsumerConfig {

    @Bean
    DefaultErrorHandler stockCommandErrorHandler(RetryProperties retryProperties, DeadLetterProperties deadLetterProperties,
                                                 KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, failure) -> new TopicPartition(deadLetterProperties.getTopic(), record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff(retryProperties));
        handler.addNotRetryableExceptions(JacksonException.class, UnknownStockCommandException.class,
                InvalidStockCommandException.class);
        return handler;
    }

    // 11장 결정 4: 서비스가 뜰 때 실패 모음 토픽이 없으면 만든다(있으면 그대로 둔다). 단일 브로커라 복제본은 하나다
    @Bean
    NewTopic stockCommandDeadLetterTopic(DeadLetterProperties deadLetterProperties) {
        return TopicBuilder.name(deadLetterProperties.getTopic())
                .partitions(deadLetterProperties.getPartitions())
                .replicas(1)
                .build();
    }

    static ExponentialBackOffWithMaxRetries backOff(RetryProperties retryProperties) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retryProperties.getMaxRetries());
        backOff.setInitialInterval(retryProperties.getInitialInterval().toMillis());
        backOff.setMultiplier(retryProperties.getMultiplier());
        backOff.setMaxInterval(retryProperties.getMaxInterval().toMillis());
        return backOff;
    }
}
