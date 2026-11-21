package com.example.consumercomparison;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * 비교 구현(11장 결정 4, 로그만 남김 — 결정 3까지의 운영): 다시 시도는 운영과 같고, 다시 시도를 다 쓴 메시지와 영구 오류 메시지는 로그만 남기고 넘어간다.
 * 주문 서비스·재고 서비스에 끼우는 시험 구성품이다. 결제 서버 시험이 훑는 패키지 밖에 둔다.
 */
@Configuration(proxyBeanMethods = false)
public class LogOnlyErrorHandling {

    @Bean
    @Primary
    DefaultErrorHandler logOnlyErrorHandler(Environment environment) {
        return ComparisonRetry.handler(environment, null);
    }
}
