package com.example.inventory.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 10장: 예약을 남기고 바꾼 때를 남기는 시계 */
@Configuration
public class InventoryConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
