package com.example.consumercomparison;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * 비교 구현(11장 결정 3, Spring Kafka 기본 처리 — 10장까지의 받는 쪽): 처리에 실패하면 간격 없이(FixedBackOff(0, 9)) 다시 시도하고,
 * 10번째 실패에 로그만 남기고 넘어간다. 영구 오류도 가리지 않는다(Spring Kafka 가 기본으로 가리는 역직렬화 오류 등만 빼고).
 * 주문 서비스·재고 서비스에 끼우는 시험 구성품이다. 결제 서버 시험이 훑는 패키지(com.example.payment) 밖에 둔다.
 */
@Configuration(proxyBeanMethods = false)
public class SpringDefaultErrorHandling {

    @Bean
    @Primary
    DefaultErrorHandler springDefaultErrorHandler() {
        return new DefaultErrorHandler();
    }
}
