package com.example.inventory.infrastructure.kafka;

import com.example.inventory.domain.error.exception.InvalidStockCommandException;
import com.example.inventory.domain.error.exception.UnknownStockCommandException;
import com.example.inventory.global.config.RetryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

/**
 * 11장 결정 3: 재고 명령 처리에 실패하면 같은 자리에서(뒤 명령을 읽지 않고) 간격을 늘려 가며 다시 시도한다(blocking, 주문 서비스와 같은 규칙).
 * 다시 시도해도 결과가 같은 영구 오류(읽을 수 없는 본문, 모르는 명령, 명령 번호·예약 번호가 없거나 명령 번호가 숫자가 아님)는 다시 시도하지 않는다.
 * 그 밖의 오류(DB 가 쓰기를 거부함 등)는 일시 오류로 본다.
 */
@Configuration
@EnableConfigurationProperties(RetryProperties.class)
public class KafkaConsumerConfig {

    @Bean
    DefaultErrorHandler stockCommandErrorHandler(RetryProperties retryProperties) {
        DefaultErrorHandler handler = new DefaultErrorHandler(backOff(retryProperties));
        handler.addNotRetryableExceptions(JacksonException.class, UnknownStockCommandException.class,
                InvalidStockCommandException.class);
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
