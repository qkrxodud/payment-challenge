package com.example.order.infrastructure.kafka;

import com.example.order.domain.error.exception.InvalidPaymentEventException;
import com.example.order.domain.error.exception.UnknownPaymentStatusException;
import com.example.order.global.config.RetryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

/**
 * 11장 결정 3: 결제 이벤트 처리에 실패하면 같은 자리에서(뒤 이벤트를 읽지 않고) 간격을 늘려 가며 다시 시도한다(blocking).
 * 같은 결제(같은 키)의 다음 이벤트는 앞 이벤트가 끝날 때까지 기다려, 다시 시도하는 동안에도 결제 서버가 보낸 순서대로 반영한다.
 * 다시 시도해도 결과가 같은 영구 오류(읽을 수 없는 본문, 모르는 결제 상태, 고유 번호가 없거나 숫자가 아님)는 다시 시도하지 않는다 —
 * 같은 파티션의 뒤 이벤트를 다시 시도 시간만큼 붙잡지 않는다. 그 밖의 오류(DB 가 쓰기를 거부함 등)는 일시 오류로 본다.
 * Spring Boot 는 이 처리기 빈을 리스너 컨테이너에 그대로 쓴다.
 */
@Configuration
@EnableConfigurationProperties(RetryProperties.class)
public class KafkaConsumerConfig {

    @Bean
    DefaultErrorHandler paymentEventErrorHandler(RetryProperties retryProperties) {
        DefaultErrorHandler handler = new DefaultErrorHandler(backOff(retryProperties));
        handler.addNotRetryableExceptions(JacksonException.class, UnknownPaymentStatusException.class,
                InvalidPaymentEventException.class);
        return handler;
    }

    static ExponentialBackOffWithMaxRetries backOff(RetryProperties retryProperties) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retryProperties.getMaxRetries());
        backOff.setInitialInterval(retryProperties.getInitialInterval().toMillis());
        backOff.setMultiplier(retryProperties.getMultiplier());
        backOff.setMaxInterval(retryProperties.getMaxInterval().toMillis());
        return backOff;
    }
}
