package com.example.order.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 9장: 받은 때를 남기는 시계 */
@Configuration
public class OrderServiceConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
