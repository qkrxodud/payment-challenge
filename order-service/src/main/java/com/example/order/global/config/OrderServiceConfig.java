package com.example.order.global.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 9장: 받은 때를 남기는 시계. 12장 결정 5부터 받은 기록의 주기 정리를 켠다 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(CleanupProperties.class)
public class OrderServiceConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
