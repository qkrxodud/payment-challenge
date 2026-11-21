package com.example.consumercomparison;

import com.example.order.domain.error.exception.InvalidPaymentEventException;
import com.example.order.domain.error.exception.UnknownPaymentStatusException;
import com.example.order.global.config.RetryProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import tools.jackson.core.JacksonException;

/**
 * 비교 구현(11장 결정 3, 다시 시도 토픽 — non-blocking): 처리에 실패한 결제 이벤트를 다시 시도 토픽(<토픽>-retry-…)으로 넘기고 원래 토픽의 뒤 이벤트는
 * 이어서 처리한다. 다시 시도 간격·횟수와 다시 시도하지 않는 영구 오류는 운영(결정 3)과 같게 두고, 다시 시도를 다 쓴 이벤트는 <토픽>.DLT 에 모은다.
 * 같은 결제(같은 키)의 뒤 이벤트가 앞 이벤트보다 먼저 처리될 수 있다. 주문 서비스에 끼우는 시험 구성품이다.
 */
@Configuration(proxyBeanMethods = false)
@EnableKafkaRetryTopic
public class RetryTopicOrderConsumer {

    @Bean
    RetryTopicConfiguration paymentEventRetryTopics(KafkaTemplate<String, String> kafkaTemplate, RetryProperties retryProperties,
                                                    Environment environment) {
        return RetryTopicConfigurationBuilder.newInstance()
                .includeTopic(environment.getRequiredProperty("order.payment-events.topic"))
                .exponentialBackoff(retryProperties.getInitialInterval().toMillis(), retryProperties.getMultiplier(),
                        retryProperties.getMaxInterval().toMillis())
                .maxAttempts(retryProperties.getMaxRetries() + 1)
                .notRetryOn(List.of(JacksonException.class, UnknownPaymentStatusException.class, InvalidPaymentEventException.class))
                .dltSuffix(".DLT")
                .autoCreateTopicsWith(2, (short) 1)
                .create(kafkaTemplate);
    }

    // 다시 시도 토픽은 다음 시도 때까지 파티션을 멈췄다 다시 여는 데 스케줄러가 있어야 한다(없으면 뜨지 않는다 — 실행으로 봄)
    @Bean(destroyMethod = "destroy")
    RetryTopicSchedulerWrapper retryTopicScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        return new RetryTopicSchedulerWrapper(scheduler);
    }
}
