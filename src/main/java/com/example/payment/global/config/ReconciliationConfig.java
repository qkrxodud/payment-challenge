package com.example.payment.global.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 결과 모름 보정에 쓰는 시계와 주기 실행(4장). 도메인은 시각을 인자로 받고, 시각은 이 시계에서 얻는다 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ReconciliationProperties.class)
public class ReconciliationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
