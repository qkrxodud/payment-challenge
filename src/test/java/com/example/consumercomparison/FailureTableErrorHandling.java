package com.example.consumercomparison;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * 비교 구현(11장 결정 4, DB 실패 표): 다시 시도는 운영과 같고, 다시 시도를 다 쓴 메시지와 영구 오류 메시지를 받는 쪽 자기 DB 의 실패 표(failed_message)에
 * 원래 토픽·파티션·위치·키·본문과 원인(예외 종류)으로 남긴다. 받는 쪽 DB 가 쓰기를 거부하는 동안에는 실패 표에도 남기지 못한다 —
 * 남기지 못하면 Spring Kafka 는 그 자리를 넘어가지 않고 다시 시도한다. 주문 서비스·재고 서비스에 끼우는 시험 구성품이다.
 */
@Configuration(proxyBeanMethods = false)
public class FailureTableErrorHandling {

    @Bean
    InitializingBean failedMessageTable(JdbcTemplate jdbcTemplate) {
        return () -> jdbcTemplate.execute("create table if not exists failed_message (id bigint not null auto_increment, "
                + "topic varchar(255) not null, partition_no int not null, offset_no bigint not null, message_key varchar(255), "
                + "body text, cause varchar(1000) not null, failed_at datetime(6) not null default current_timestamp(6), primary key (id))");
    }

    @Bean
    @Primary
    DefaultErrorHandler failureTableErrorHandler(Environment environment, JdbcTemplate jdbcTemplate) {
        return ComparisonRetry.handler(environment, (record, failure) -> store(jdbcTemplate, record, failure));
    }

    private static void store(JdbcTemplate jdbcTemplate, ConsumerRecord<?, ?> record, Exception failure) {
        Throwable cause = rootOf(failure);
        jdbcTemplate.update("insert into failed_message (topic, partition_no, offset_no, message_key, body, cause) values (?, ?, ?, ?, ?, ?)",
                record.topic(), record.partition(), record.offset(), textOf(record.key()), textOf(record.value()),
                cause.getClass().getName());
    }

    private static Throwable rootOf(Throwable failure) {
        Throwable cause = failure;
        while (Objects.nonNull(cause.getCause()) && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String textOf(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (Objects.isNull(value)) {
            return null;
        }
        return value.toString();
    }
}
