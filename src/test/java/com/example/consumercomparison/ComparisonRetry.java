package com.example.consumercomparison;

import com.example.inventory.domain.error.exception.InvalidStockCommandException;
import com.example.inventory.domain.error.exception.UnknownStockCommandException;
import com.example.order.domain.error.exception.InvalidPaymentEventException;
import com.example.order.domain.error.exception.UnknownPaymentStatusException;
import java.time.Duration;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

/**
 * 11장 결정 4 비교 구현이 함께 쓰는 다시 시도 규칙: 운영(결정 3)과 같은 간격·횟수(order.retry.* 또는 inventory.retry.*)와 같은 영구 오류.
 * 다시 시도를 다 쓴 뒤에 하는 일(recoverer)만 비교 구현마다 다르다. 주문 서비스·재고 서비스 어느 쪽에 끼워도 쓴다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class ComparisonRetry {

    static DefaultErrorHandler handler(Environment environment, ConsumerRecordRecoverer recoverer) {
        String prefix = Objects.nonNull(environment.getProperty("order.retry.max-retries")) ? "order.retry." : "inventory.retry.";
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(
                environment.getRequiredProperty(prefix + "max-retries", Integer.class));
        backOff.setInitialInterval(environment.getRequiredProperty(prefix + "initial-interval", Duration.class).toMillis());
        backOff.setMultiplier(environment.getRequiredProperty(prefix + "multiplier", Double.class));
        backOff.setMaxInterval(environment.getRequiredProperty(prefix + "max-interval", Duration.class).toMillis());
        DefaultErrorHandler handler = Objects.isNull(recoverer) ? new DefaultErrorHandler(backOff) : new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JacksonException.class, UnknownPaymentStatusException.class, InvalidPaymentEventException.class,
                UnknownStockCommandException.class, InvalidStockCommandException.class);
        return handler;
    }
}
